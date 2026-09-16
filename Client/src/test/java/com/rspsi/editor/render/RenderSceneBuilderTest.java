package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

class RenderSceneBuilderTest {
    @Test
    void buildsTerrainForEveryPlaneAndExposesCanonicalObjects() {
        WorldDocument document = new WorldDocument(2, 3, 2);
        WorldObject object = new WorldObject(100, 10, 2, 1, 1, 2);
        document.tile(1, 1, 2).restore(new TileSnapshot(
                10, 20, 30, 40, 2, 3, 6, 1, 0, List.of(object)));

        RenderScene scene = new RenderSceneBuilder().build(document);

        assertSame(document, scene.document());
        assertEquals(12, scene.terrainMeshes().size());
        assertEquals(1, scene.objects().size());
        assertEquals(object, scene.objects().get(0));
        assertEquals(6, scene.terrainMeshes()
                .get(new TileCoordinate(1, 1, 2)).vertices().size());
    }

    @Test
    void compatibilityConstructorDoesNotInventDerivedGeometry() {
        WorldDocument document = new WorldDocument(1, 1, 1);

        RenderScene scene = new RenderScene(document);

        assertSame(document, scene.document());
        assertEquals(0, scene.terrainMeshes().size());
        assertEquals(0, scene.objects().size());
        assertEquals(0, scene.bridges().size());
        assertEquals(0, scene.terrainMaterials().size());
    }

    @Test
    void updateRebuildsOnlyDirtyTerrainAndRefreshesObjects() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        RenderSceneBuilder builder = new RenderSceneBuilder();
        RenderScene initial = builder.build(document);
        var untouched = initial.terrainMeshes().get(new TileCoordinate(0, 1, 0));

        WorldObject object = new WorldObject(200, 10, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                20, 20, 20, 20, 0, 0, 0, 0, 0, List.of(object)));
        RenderScene updated = builder.update(initial,
                new RenderChanges(Set.of(new TileCoordinate(0, 0, 0))));

        assertSame(untouched, updated.terrainMeshes().get(new TileCoordinate(0, 1, 0)));
        org.junit.jupiter.api.Assertions.assertNotSame(
                initial.terrainMeshes().get(new TileCoordinate(0, 0, 0)),
                updated.terrainMeshes().get(new TileCoordinate(0, 0, 0)));
        assertEquals(List.of(object), updated.objects());
    }

    @Test
    void exposesBridgeLinksDerivedFromTheCanonicalFlag() {
        WorldDocument document = new WorldDocument(1, 1, 4);
        document.tile(1, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, com.rspsi.editor.model.OsrsTileFlags.BRIDGE, List.of()));

        RenderScene scene = new RenderSceneBuilder().build(document);

        assertEquals(3, scene.bridges().size());
        assertEquals(new com.rspsi.editor.model.BridgeLink(
                new TileCoordinate(1, 0, 0), new TileCoordinate(0, 0, 0)), scene.bridges().get(0));
        assertEquals(scene.bridges(), document.bridgeLinks());
    }

    @Test
    void expandsDirtyChunksToClampedCoordinatesAcrossAllPlanes() {
        WorldDocument document = new WorldDocument(10, 9, 2);

        RenderChanges changes = RenderChanges.fromDirtyRegions(Set.of(
                new DirtyRegion(1, 1, false, false, false, false, true)), document);

        assertEquals(4, changes.dirtyTiles().size());
        assertEquals(Set.of(
                new TileCoordinate(0, 8, 8), new TileCoordinate(0, 9, 8),
                new TileCoordinate(1, 8, 8), new TileCoordinate(1, 9, 8)),
                changes.dirtyTiles());
    }

    @Test
    void acceptsSessionChunkInvalidationBatchesDirectly() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        RenderSceneBuilder builder = new RenderSceneBuilder();
        RenderScene initial = builder.build(document);
        document.tile(0, 4, 4).restore(new TileSnapshot(
                20, 20, 20, 20, 0, 4, 0, 0, 0, List.of()));

        RenderScene updated = builder.update(initial, Set.of(
                new DirtyRegion(0, 0, false, false, false, false, true)));

        assertEquals(20, updated.terrainMeshes()
                .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
    }

    @Test
    void definitionAwareBuilderCarriesNeutralTerrainMaterialInputs() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 2, 3, 0, 0, 0, List.of()));

        RenderScene scene = new RenderSceneBuilder(definitions()).build(document);

        assertEquals(new TerrainMaterial(2, 3, 17, 0x102030, 0xA0B0C0),
                scene.terrainMaterials().get(new TileCoordinate(0, 0, 0)));
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 2 ? Optional.of(new FloorDefinitionView(id, -1, 0x102030,
                        0, 0, 0, 0, 0)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 3 ? Optional.of(new FloorDefinitionView(id, 17, 0xA0B0C0,
                        0, 0, 0, 0, 0)) : Optional.empty();
            }
        };
    }

    @Test
    void sessionSceneControllerPublishesInitialAndChunkUpdates() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        EditorSession session = new EditorSession(document);
        RecordingRenderer renderer = new RecordingRenderer();

        try (SessionSceneController controller = new SessionSceneController(session, renderer)) {
            assertEquals(1, renderer.loadCount);
            session.execute(new SetTileCommand(new TileCoordinate(0, 4, 4),
                    document.tile(0, 4, 4).snapshot(),
                    new TileSnapshot(20, 20, 20, 20, 0, 0, 0, 0, 0, List.of()),
                    "height edit"));

            assertEquals(1, renderer.updateCount);
            assertEquals(20, renderer.lastScene.terrainMeshes()
                    .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
            assertEquals(20, controller.scene().terrainMeshes()
                    .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
            assertTrue(session.dirtyRegions().isEmpty());
        }

        session.execute(new SetTileCommand(new TileCoordinate(0, 4, 4),
                document.tile(0, 4, 4).snapshot(),
                new TileSnapshot(24, 24, 24, 24, 0, 0, 0, 0, 0, List.of()),
                "second height edit"));
        assertEquals(1, renderer.updateCount);
    }

    private static final class RecordingRenderer implements SceneRenderer {
        private int loadCount;
        private int updateCount;
        private RenderScene lastScene;

        @Override public void load(RenderScene scene) { loadCount++; lastScene = scene; }
        @Override public void update(RenderChanges changes) { updateCount++; }
        @Override public void update(RenderScene scene, RenderChanges changes) {
            updateCount++;
            lastScene = scene;
        }
        @Override public void render(CameraState camera) { }
        @Override public java.util.Optional<PickResult> pick(float x, float y) {
            return java.util.Optional.empty();
        }
    }
}
