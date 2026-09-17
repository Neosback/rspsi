package com.rspsi.editor;

import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorSceneAccess;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.render.RenderScene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPluginSceneAccessTest {
    @Test
    void sceneAccessIsOptionalAndPublishesHostOwnedSnapshot() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        RenderScene scene = new RenderScene(session.world());
        EditorSceneAccess access = () -> EditorSceneSnapshot.from(scene);
        EditorPluginContext context = new EditorPluginContext(
                session, new EmptyAssets(), new EditorPluginRegistry(), Optional.of(access));

        assertEquals(1, context.scene().orElseThrow().snapshot().width());
        assertTrue(context.scene().orElseThrow().contains(
                new com.rspsi.editor.model.TileCoordinate(0, 0, 0)));
    }

    @Test
    void snapshotDoesNotExposeTheMutableWorldDocument() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        RenderScene scene = new RenderScene(session.world());
        EditorSceneSnapshot snapshot = EditorSceneSnapshot.from(scene);

        assertEquals(1, snapshot.tiles().size());
        assertEquals(1, session.world().width());
        snapshot.tile(new com.rspsi.editor.model.TileCoordinate(0, 0, 0))
                .orElseThrow()
                .objects();
        assertEquals(0, session.world().tile(0, 0, 0).snapshot().objects().size());
    }

    @Test
    void sceneMapsKeepStableInsertionOrderForFrontendProjections() {
        EditorSession session = new EditorSession(new WorldModel(2, 1, 1));
        var first = new com.rspsi.editor.model.TileCoordinate(0, 0, 0);
        var second = new com.rspsi.editor.model.TileCoordinate(0, 1, 0);
        Map<com.rspsi.editor.model.TileCoordinate, com.rspsi.editor.terrain.TerrainMesh> meshes =
                new LinkedHashMap<>();
        meshes.put(first, new com.rspsi.editor.terrain.TerrainMesh(List.of(), List.of()));
        meshes.put(second, new com.rspsi.editor.terrain.TerrainMesh(List.of(), List.of()));

        RenderScene scene = new RenderScene(session.world(), meshes, List.of(), List.of());
        assertEquals(List.of(first, second), scene.terrainMeshes().keySet().stream().toList());
    }

    @Test
    void tileProjectionGroupsSceneLayersAndBridgeStateForPlugins() {
        WorldModel document = new WorldModel(1, 1, 2);
        WorldObject ground = new WorldObject(2, 10, 0, 1, 0, 0);
        WorldObject secondGround = new WorldObject(3, 10, 0, 1, 0, 0);
        WorldObject wall = new WorldObject(1, 0, 0, 1, 0, 0);
        document.tile(1, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, OsrsTileFlags.BRIDGE,
                List.of(ground, wall, secondGround)));

        EditorSceneSnapshot snapshot = EditorSceneSnapshot.from(
                new com.rspsi.editor.render.RenderSceneBuilder().build(document));
        var projection = snapshot.tileProjection(new TileCoordinate(1, 0, 0)).orElseThrow();

        assertEquals(0, projection.effectivePlane());
        assertTrue(projection.hasBridge());
        assertEquals(List.of(wall, ground, secondGround),
                projection.objects().stream().map(object -> object.object()).toList());
        assertEquals(2, snapshot.tileProjections().size());
        var lower = snapshot.tileProjection(new TileCoordinate(0, 0, 0)).orElseThrow();
        assertEquals(-1, lower.effectivePlane());
        assertTrue(!lower.hasEffectiveSurface());
    }

    private static final class EmptyAssets implements AssetRepository {
        @Override public List<AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<AssetDescriptor> get(int id, String type) {
            return Optional.empty();
        }
    }
}
