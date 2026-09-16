package com.rspsi.legacy;

import com.jagex.map.MapRegion;
import com.jagex.map.SceneGraph;
import com.jagex.map.object.GameObject;
import com.jagex.map.object.Wall;
import com.jagex.map.tile.SceneTile;
import com.jagex.util.ObjectKey;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMapDocumentBridgeTest {

    @Test
    void importsTerrainFieldsAcrossAllPlanes() {
        MapRegion region = new MapRegion(null, 2, 2);
        region.tileHeights[2][1][1] = -30;
        region.tileHeights[2][2][1] = -20;
        region.tileHeights[2][2][2] = -10;
        region.tileHeights[2][1][2] = -25;
        region.underlays[2][1][1] = 7;
        region.overlays[2][1][1] = 9;
        region.overlayShapes[2][1][1] = 4;
        region.overlayOrientations[2][1][1] = 3;
        region.tileFlags[2][1][1] = 6;

        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        TileSnapshot tile = document.tile(2, 1, 1).snapshot();

        assertEquals(new TileSnapshot(-30, -20, -10, -25, 7, 9, 4, 3, 6, java.util.List.of()), tile);
    }

    @Test
    void importsOneAnchorForEachSceneObjectLayerWithoutDuplicatingFootprints() {
        MapRegion region = new MapRegion(null, 3, 3);
        SceneGraph scene = new SceneGraph(3, 3, 4);

        ObjectKey wallKey = new ObjectKey(1, 1, 100, 0, 2, true, false);
        Wall wall = new Wall(wallKey, 1, 1, 0);
        wall.setPlane(0);
        scene.tiles[0][1][1] = new SceneTile(1, 1, 0);
        scene.tiles[0][1][1].wall = wall;

        ObjectKey gameKey = new ObjectKey(0, 2, 200, 10, 1, true, true);
        GameObject game = new GameObject(gameKey, 0, 2, 0);
        game.setPlane(0);
        game.maxX = 1;
        game.maxY = 2;
        scene.tiles[0][0][2] = new SceneTile(0, 2, 0);
        scene.tiles[0][1][2] = new SceneTile(1, 2, 0);
        scene.tiles[0][0][2].gameObjects[0] = game;
        scene.tiles[0][0][2].objectCount = 1;
        scene.tiles[0][1][2].gameObjects[0] = game;
        scene.tiles[0][1][2].objectCount = 1;

        WorldDocument document = LegacyMapDocumentBridge.importDocument(region, scene);

        assertEquals(java.util.List.of(new com.rspsi.editor.model.WorldObject(100, 0, 2, 0, 1, 1)),
                document.tile(0, 1, 1).snapshot().objects());
        assertEquals(java.util.List.of(new com.rspsi.editor.model.WorldObject(200, 10, 1, 0, 0, 2)),
                document.tile(0, 0, 2).snapshot().objects());
        assertTrue(document.tile(0, 1, 2).snapshot().objects().isEmpty());
    }

    @Test
    void appliesOnlyChangedUnderlaysAndRefreshesOncePerCommand() {
        MapRegion region = new MapRegion(null, 2, 2);
        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        EditorSession session = new EditorSession(document);
        AtomicInteger refreshes = new AtomicInteger();
        LegacyMapDocumentBridge bridge = new LegacyMapDocumentBridge(region, null, refreshes::incrementAndGet);
        bridge.attach(session);

        TileCoordinate coordinate = new TileCoordinate(3, 1, 1);
        TileSnapshot before = document.tile(coordinate).snapshot();
        TileSnapshot after = new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(), 12, before.overlayId(),
                before.overlayShape(), before.overlayRotation(), before.flags(), before.objects());

        session.execute(new SetTileCommand(coordinate, before, after, "paint"));
        assertEquals(12, region.underlays[3][1][1]);
        assertEquals(1, refreshes.get());

        session.undo();
        assertEquals(0, region.underlays[3][1][1]);
        assertEquals(2, refreshes.get());

        session.redo();
        assertEquals(12, region.underlays[3][1][1]);
        assertEquals(3, refreshes.get());
        bridge.close();
    }

    @Test
    void synchronizesHeightsFloorsAndFlagsThroughTheSameSessionListener() {
        MapRegion region = new MapRegion(null, 2, 2);
        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        EditorSession session = new EditorSession(document);
        AtomicInteger refreshes = new AtomicInteger();
        LegacyMapDocumentBridge bridge = new LegacyMapDocumentBridge(region, null, refreshes::incrementAndGet);
        bridge.attach(session);

        TileCoordinate coordinate = new TileCoordinate(1, 1, 1);
        TileSnapshot before = document.tile(coordinate).snapshot();
        TileSnapshot after = new TileSnapshot(-40, -30, -20, -10, 4, 5, 6, 3, 0x06, before.objects());

        session.execute(new SetTileCommand(coordinate, before, after, "edit tile"));

        assertEquals(-40, region.tileHeights[1][1][1]);
        assertEquals(-30, region.tileHeights[1][2][1]);
        assertEquals(-20, region.tileHeights[1][2][2]);
        assertEquals(-10, region.tileHeights[1][1][2]);
        assertEquals(4, region.underlays[1][1][1]);
        assertEquals(5, region.overlays[1][1][1]);
        assertEquals(6, region.overlayShapes[1][1][1]);
        assertEquals(3, region.overlayOrientations[1][1][1]);
        assertEquals(0x06, region.tileFlags[1][1][1]);
        assertEquals(1, region.manualTileHeight[1][1][1]);
        assertEquals(1, refreshes.get());
    }

    @Test
    void invalidChangedTilesAreIgnoredWithoutRefresh() {
        MapRegion region = new MapRegion(null, 2, 2);
        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        AtomicInteger refreshes = new AtomicInteger();
        LegacyMapDocumentBridge bridge = new LegacyMapDocumentBridge(region, null, refreshes::incrementAndGet);
        EditorSession session = new EditorSession(document);
        bridge.attach(session);

        bridge.changed(java.util.Set.of(new TileCoordinate(0, 99, 0)));
        assertEquals(0, refreshes.get());
    }
}
