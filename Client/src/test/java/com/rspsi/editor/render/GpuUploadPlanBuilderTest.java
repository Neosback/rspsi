package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;
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
                0, 0, 0, 128, 32, 128, false, false);
        SceneLayer terrainLayer = new SceneLayer(SceneLayer.Kind.TERRAIN, List.of());
        SceneLayer objectLayer = new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0));
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
        assertEquals(23, plan.commands().get(1).depthBias());
        assertFalse(plan.fingerprint().isBlank());
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
}
