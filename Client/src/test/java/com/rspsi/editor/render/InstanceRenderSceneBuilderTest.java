package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.collision.CollisionFlag;
import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRenderSceneBuilderTest {
    @Test
    void projectedChunkFlowsThroughTerrainModelsCollisionAndGpuWorldSpace() {
        DefinitionProvider definitions = definitions();
        WorldDocument sourceDocument = new WorldDocument(64, 64, 1);
        sourceDocument.tile(0, 1, 2).restore(new TileSnapshot(
                0, 16, 32, 8,
                1, 0, 0, 0, 0,
                List.of(new WorldObject(42, 10, 0, 0, 1, 2))));

        WorldRegion sourceRegion = new WorldRegion(0, 0, sourceDocument);
        WorldRegionWindow source = new WorldRegionWindow(
                0, 0, 1, 1, Map.of(sourceRegion.regionId(), sourceRegion));
        InstanceChunkTemplate template = new InstanceChunkTemplate(
                0, 2, 3,
                0, 0, 0, 1);
        SceneWindow window = new SceneWindow(
                source, 3200, 6400, 1, 0, 0, -1,
                Set.of(sourceRegion.regionId()), List.of(template));

        RenderScene scene = new InstanceRenderSceneBuilder(definitions).build(window);

        // Source (1,2) rotated once inside the chunk becomes (2,6), then
        // lands in target chunk (2,3) at local scene tile (18,30).
        TileCoordinate target = new TileCoordinate(0, 18, 30);
        assertNotNull(scene.terrainPackets().get(target),
                "projected terrain must enter the ordinary terrain compiler");

        ModelRenderPacket model = scene.modelPackets().stream()
                .filter(packet -> packet.anchor().equals(target))
                .findFirst().orElseThrow();
        assertEquals(42, model.objectId());
        assertEquals(1, model.sceneObjectIdentity().rotation());

        int collision = scene.collision().get(target).rawFlags();
        assertTrue((collision & CollisionFlag.LOC) != 0,
                "projected object must participate in ordinary scene collision");

        GpuScenePacket packet = new GpuScenePacketBuilder().buildInstance(window, scene);
        GpuUploadPlan upload = new GpuUploadPlanBuilder().build(packet);
        GpuDrawCommand command = upload.commands().stream()
                .filter(value -> value.objectId() == 42)
                .findFirst().orElseThrow();

        assertEquals(3218, command.modelAnchorX());
        assertEquals(6430, command.modelAnchorY());
        assertEquals(3218, command.sceneObjectIdentity().anchorX());
        assertEquals(6430, command.sceneObjectIdentity().anchorY());
        assertTrue(upload.vertices().stream().anyMatch(vertex ->
                        vertex.pickerTileX() == 3218 && vertex.pickerTileY() == 6430),
                "instance GPU geometry and picker payload must use target world coordinates");
    }

    private static DefinitionProvider definitions() {
        ObjectDefinitionView object = new ObjectDefinitionView(
                42, "instance object", 1, 1, List.of(),
                new int[]{7}, new int[]{10}, -1, true);
        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{-1});

        return new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                return id == 42 ? Optional.of(object) : Optional.empty();
            }

            @Override
            public Optional<ObjectCollisionView> objectCollision(int id) {
                return id == 42
                        ? Optional.of(new ObjectCollisionView(
                                42, 1, 1, 2, false, false))
                        : Optional.empty();
            }

            @Override
            public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return id == 42 ? Optional.of(ObjectAppearanceView.empty()) : Optional.empty();
            }

            @Override
            public Optional<ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(geometry) : Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return id == 0
                        ? Optional.of(new FloorDefinitionView(
                                0, -1, 0x406080, 64, 96, 96, 64, 128))
                        : Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }
}
