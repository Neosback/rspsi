package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GpuUploadPlanBuilderTest {
    @Test
    void flattensTerrainAndModelsIntoWorldSpaceWithStableCommands() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 42, ObjectCategory.GROUND,
                List.of(new ModelVertex(64, 0, 64, 1, 2, 3, 1, 0, 0),
                        new ModelVertex(96, 0, 64, 1, 2, 3, 1, 1, 0),
                        new ModelVertex(64, 32, 64, 1, 2, 3, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 7, 8, -1, -1, 0, 2, 1,
                        0, 0, 1, 0, 0, 1, 7, 23),
                        new ModelTriangle(0, 1, 2, 7, 8, -1, -1, 255, 2, 1,
                        0, 0, 1, 0, 0, 1, 7, 23)), List.of(), -1,
                0, 0, 0, 128, 32, 128, false, false)
                .withRenderMode(GpuDrawCommand.RenderMode.SORTED_NO_DEPTH);
        SceneLayer terrainLayer = new SceneLayer(SceneLayer.Kind.TERRAIN, List.of());
        SceneLayer objectLayer = new SceneLayer(SceneLayer.Kind.WALL_DECORATION, List.of(0));
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(model),
                List.of(terrainLayer, objectLayer), List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, List.of(12850).stream().collect(java.util.stream.Collectors.toSet()), List.of()),
                List.of(tile), LightingProfile.osrs(), "source-fingerprint", Map.of());

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(6, plan.vertices().size());
        assertEquals(6, plan.indices().size());
        assertEquals(2, plan.commands().size());
        assertEquals(3200 * 128.0f, plan.vertices().get(0).x());
        assertEquals(3200 * 128.0f, plan.vertices().get(0).z());
        assertEquals(3200 * 128.0f + 64.0f, plan.vertices().get(3).x());
        assertEquals(GpuColorEncoding.PACKED_JAGEX_HSL, plan.vertices().get(0).colorEncoding());
        assertEquals(7, plan.vertices().get(3).encodedColor());
        assertEquals(7, plan.vertices().get(4).encodedColor());
        assertEquals(7, plan.vertices().get(5).encodedColor());
        assertEquals(3, plan.commands().get(0).indexCount());
        assertEquals(3, plan.commands().get(1).firstIndex());
        // Authored face bias (23) plus this face's own priority (2): wall
        // decorations fold priority into the bias so a model's own coplanar
        // priority layers separate in depth, not just the flat minimum step.
        assertEquals(25, plan.commands().get(1).depthBias());
        assertEquals(2, plan.commands().get(1).priority(),
                "wall decorations preserve authored model face priority");
        assertEquals(GpuDrawCommand.RenderMode.SORTED_NO_DEPTH,
                plan.commands().get(1).renderMode());
        assertFalse(plan.fingerprint().isBlank());
    }

    @Test
    void everyVertexCarriesItsTileOrObjectsPickerPayloadBroadcast() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 42, ObjectCategory.GROUND,
                List.of(new ModelVertex(64, 0, 64, 1, 2, 3, 1, 0, 0),
                        new ModelVertex(96, 0, 64, 1, 2, 3, 1, 1, 0),
                        new ModelVertex(64, 32, 64, 1, 2, 3, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 7, 8, -1, -1, 0, 2, 1,
                        0, 0, 1, 0, 0, 1, 7, 23)), List.of(), -1,
                0, 0, 0, 128, 32, 128, false, false);
        SceneLayer terrainLayer = new SceneLayer(SceneLayer.Kind.TERRAIN, List.of());
        SceneLayer objectLayer = new SceneLayer(SceneLayer.Kind.WALL_DECORATION, List.of(0));
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(model),
                List.of(terrainLayer, objectLayer), List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "picker-payload", Map.of());

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        // Terrain triangle: 3 vertices, all tagged with this tile's plane/x/y and the terrain slot.
        for (int i = 0; i < 3; i++) {
            GpuSceneVertex vertex = plan.vertices().get(i);
            assertEquals(0, vertex.pickerPlane());
            assertEquals(3200, vertex.pickerTileX());
            assertEquals(3200, vertex.pickerTileY());
            assertEquals(PickerId.terrainSlot(), vertex.pickerSlot());
        }

        // Model triangle: 3 vertices, all tagged with the same tile address and the
        // WALL_DECORATION layer's slot - not the terrain slot.
        for (int i = 3; i < 6; i++) {
            GpuSceneVertex vertex = plan.vertices().get(i);
            assertEquals(0, vertex.pickerPlane());
            assertEquals(3200, vertex.pickerTileX());
            assertEquals(3200, vertex.pickerTileY());
            assertEquals(PickerId.slotFor(SceneLayer.Kind.WALL_DECORATION), vertex.pickerSlot());
            assertNotEquals(PickerId.terrainSlot(), vertex.pickerSlot());
        }
    }

    @Test
    void modelAlphaUsesOpaqueBlendedAndInvisibleFaceClasses() {
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        List<ModelVertex> vertices = List.of(
                new ModelVertex(0, 0, 0, 1, 0, 0, 1, 0, 0),
                new ModelVertex(128, 0, 0, 1, 0, 0, 1, 1, 0),
                new ModelVertex(0, 0, 128, 1, 0, 0, 1, 0, 1));
        List<ModelTriangle> faces = List.of(
                new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 0, 0, 0),
                new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 128, 0, 0),
                new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 255, 0, 0));
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 7,
                ObjectCategory.GROUND, vertices, faces, List.of(), -1,
                0, 0, 0, 128, 0, 128, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "alpha-classes", Map.of());

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(2, plan.commands().size());
        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
        assertEquals(GpuDrawCommand.SubmissionPass.ALPHA, plan.commands().get(1).pass());
        assertEquals(6, plan.vertices().size());
    }

    @Test
    void textureRecordFlagDoesNotMoveAnOpaqueFaceIntoTheAlphaPass() {
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 8, ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 1, 0, 0, 1, 0, 0),
                        new ModelVertex(128, 0, 0, 1, 0, 0, 1, 1, 0),
                        new ModelVertex(0, 0, 128, 1, 0, 0, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 1, 1, 1, 7, 0, 0, 0)), List.of(), -1,
                0, 0, 0, 128, 0, 128, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        RenderTextureResource texture = new RenderTextureResource(7,
                new TextureDefinitionView(7, true, 7, 0x336699, 0, 0, false),
                1, 1, new int[]{0x336699}, RenderTextureResource.PixelStatus.AVAILABLE, "");
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "transparent-material", Map.of(7, texture));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(1, plan.commands().size());
        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
    }

    @Test
    void transparentTexturePixelsRemainOpaqueForDepthOwnership() {
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 9, ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 1, 0, 0, 1, 0, 0),
                        new ModelVertex(128, 0, 0, 1, 0, 0, 1, 1, 0),
                        new ModelVertex(0, 0, 128, 1, 0, 0, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 1, 1, 1, 7, 0, 0, 0)), List.of(), -1,
                0, 0, 0, 128, 0, 128, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        RenderTextureResource texture = new RenderTextureResource(7,
                new TextureDefinitionView(7, false, 7, 0x336699, 0, 0, false),
                1, 1, new int[]{0}, RenderTextureResource.PixelStatus.AVAILABLE, "");
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "transparent-pixels", Map.of(7, texture));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
    }

    @Test
    void alphaCarryingFloorTextureStaysInTheDepthWritingPass() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 64, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 80, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 96, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 1, 17, 255, 0)),
                1, 0, 17, -1, -1, false, false, -1);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);
        // Water-style ARGB texture: opaque, half and fully transparent texels.
        RenderTextureResource water = new RenderTextureResource(17,
                new TextureDefinitionView(17, true, 17, 0x3A5F9E, 0, 0, false),
                2, 2, new int[]{0xFF3A5F9E, 0x803A5F9E, 0x003A5F9E, 0x003A5F9E},
                RenderTextureResource.PixelStatus.AVAILABLE, "");
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "alpha-floor", Map.of(17, water));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        // The client's floor scanline spends the texture's alpha by mixing the
        // texel toward the tile's flat colour and then writing the result
        // opaquely, so a translucent floor texture never leaves the pass that
        // owns depth.
        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
    }

    @Test
    void opaqueFloorTextureStaysInTheDepthWritingPass() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 64, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 80, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 96, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 1, 7, 255, 0)),
                1, 0, 7, -1, -1, false, false, -1);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);
        RenderTextureResource grass = new RenderTextureResource(7,
                new TextureDefinitionView(7, false, 7, 0x4A7C3F, 0, 0, false),
                2, 2, new int[]{0x4A7C3F, 0x518444, 0x446E39, 0x5A8F4B},
                RenderTextureResource.PixelStatus.AVAILABLE, "");
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "opaque-floor", Map.of(7, grass));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertFalse(grass.usesAlphaChannel());
        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
    }

    @Test
    void modelTextureAlphaStillKeepsCutoutsInTheDepthWritingPass() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, 8, ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 1, 0, 0, 1, 0, 0),
                        new ModelVertex(128, 0, 0, 1, 0, 0, 1, 1, 0),
                        new ModelVertex(0, 0, 128, 1, 0, 0, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 1, 1, 1, 17, 0, 0, 0)), List.of(), -1,
                0, 0, 0, 128, 0, 128, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        RenderTextureResource foliage = new RenderTextureResource(17,
                new TextureDefinitionView(17, true, 17, 0x3A5F9E, 0, 0, false),
                2, 2, new int[]{0xFF3A5F9E, 0x803A5F9E, 0x003A5F9E, 0x003A5F9E},
                RenderTextureResource.PixelStatus.AVAILABLE, "");
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "model-cutout", Map.of(17, foliage));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE, plan.commands().get(0).pass());
    }

    @Test
    void textureAndGeometryChangesProduceDifferentUploadFingerprints() {
        GpuScenePacket empty = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(), LightingProfile.osrs(), "source-fingerprint", Map.of());
        GpuUploadPlan first = new GpuUploadPlanBuilder().build(empty);
        GpuScenePacket changed = new GpuScenePacket(empty.window(), empty.tiles(), empty.lightingProfile(),
                "different-source-fingerprint", empty.textures());

        assertNotEquals(first.fingerprint(), new GpuUploadPlanBuilder().build(changed).fingerprint());
    }

    @Test
    void texturedTerrainUsesLightnessEncodingRatherThanPackedHsl() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 64, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 80, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 96, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 1, 7, 255, 0)),
                1, 0, 7, -1, -1, false, false, -1);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "textured-terrain", Map.of());

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(GpuColorEncoding.TEXTURE_LIGHTNESS,
                plan.vertices().get(0).colorEncoding());
        assertEquals(1, plan.commands().get(0).depthBias());
    }

    @Test
    void contiguousTerrainTilesMergeIntoSingleDrawCommand() {
        WorldTileAddress a1 = WorldTileAddress.of(3200, 3200, 0);
        TileCoordinate c1 = new TileCoordinate(0, 3200, 3200);
        TerrainRenderPacket t1 = new TerrainRenderPacket(c1,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        SceneTileSnapshot tile1 = new SceneTileSnapshot(c1, a1, 0, 0,
                Optional.empty(), Optional.of(t1), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);

        WorldTileAddress a2 = WorldTileAddress.of(3201, 3200, 0);
        TileCoordinate c2 = new TileCoordinate(0, 3201, 3200);
        TerrainRenderPacket t2 = new TerrainRenderPacket(c2,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        SceneTileSnapshot tile2 = new SceneTileSnapshot(c2, a2, 0, 0,
                Optional.empty(), Optional.of(t2), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);

        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3200, 3200, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile1, tile2), LightingProfile.osrs(), "merge-test", Map.of());

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        assertEquals(6, plan.vertices().size());
        assertEquals(6, plan.indices().size());
        // Both tiles merged into exactly ONE draw command
        assertEquals(1, plan.commands().size());
        assertEquals(6, plan.commands().get(0).indexCount());
    }

    @Test
    void wallDecorationPreservesMultiPriorityFaceHierarchy() {
        TileCoordinate coordinate = new TileCoordinate(0, 3213, 3218);
        WorldTileAddress address = WorldTileAddress.of(3213, 3218, 0);

        List<ModelVertex> vertices = List.of(
                new ModelVertex(-64, 0, 0, 1, 0, 0, 1, 0, 0),
                new ModelVertex(-64, 32, 0, 1, 0, 0, 1, 1, 0),
                new ModelVertex(-64, 0, 32, 1, 0, 0, 1, 0, 1));

        // Three coplanar faces mimicking Object 899 (hanging banner):
        // cloth (priority 0, textured), trim (priority 1), crest (priority 3)
        List<ModelTriangle> faces = List.of(
                new ModelTriangle(0, 1, 2, 1, 1, 1, 16, 0, 0, 0),
                new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 0, 1, 0),
                new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 0, 3, 0));

        ModelRenderPacket banner = new ModelRenderPacket(coordinate, 899,
                ObjectCategory.WALL_DECOR, vertices, faces, List.of(), -1,
                0, 0, 0, 128, 128, 128, false, false);

        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(banner),
                List.of(new SceneLayer(SceneLayer.Kind.WALL_DECORATION, List.of(0))),
                List.of(), false, false);

        RenderTextureResource bannerTexture = new RenderTextureResource(16,
                new TextureDefinitionView(16, false, 16, 0x880000, 0, 0, false),
                1, 1, new int[]{0x880000}, RenderTextureResource.PixelStatus.AVAILABLE, "");

        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1,
                        Map.of()), 3213, 3218, 1, 0, java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "banner-prio-test", Map.of(16, bannerTexture));

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        // Three distinct commands corresponding to the three priority bands
        assertEquals(3, plan.commands().size());

        // Priorities must match the authored face priorities 0, 1, 3, not clamped to 10
        assertEquals(0, plan.commands().get(0).priority(), "cloth retains priority 0");
        assertEquals(16, plan.commands().get(0).textureId());
        // A flat minimum bias of 1 for every face here is exactly the bug
        // Object 899 shipped with: cloth/trim/crest are coplanar, so an
        // identical bias leaves them separated only by float rounding and
        // they z-fight/flicker into each other. Depth bias must climb with
        // face priority so each layer gets its own depth step.
        assertEquals(1, plan.commands().get(0).depthBias(), "cloth (priority 0) gets the base bias step");

        assertEquals(1, plan.commands().get(1).priority(), "trim retains priority 1");
        assertEquals(-1, plan.commands().get(1).textureId());
        assertEquals(2, plan.commands().get(1).depthBias(), "trim (priority 1) sits one step above cloth");

        assertEquals(3, plan.commands().get(2).priority(), "crest retains priority 3");
        assertEquals(-1, plan.commands().get(2).textureId());
        assertEquals(4, plan.commands().get(2).depthBias(), "crest (priority 3) sits above trim, not tied with it");
    }
}
