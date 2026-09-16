package com.rspsi.editor.render;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.collision.CollisionFlag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderWindowSceneBuilderTest {
    @Test
    void buildsLoadedRegionsAtWorldAddressesWithoutFabricatingHoles() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldObject object = new WorldObject(100, 10, 0, 0, 7, 9);
        document.tile(0, 7, 9).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(object)));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(loaded.regionId(), loaded));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        assertEquals(1, window.loadedRegionCount());
        assertEquals(64 * 64 * 4, scene.terrainMeshes().size());
        WorldTileAddress objectAddress = WorldTileAddress.of(10 * 64 + 7, 20 * 64 + 9, 0);
        assertTrue(scene.hasTile(objectAddress));
        assertEquals(1, scene.objects().size());
        assertEquals(objectAddress, scene.objects().get(0).address());
        assertEquals(64 * 64 * 4, scene.collision().size());
        assertFalse(scene.hasTile(WorldTileAddress.of(11 * 64, 20 * 64, 0)));
    }

    @Test
    void publishesWorldAddressedTerrainCollisionForOverlayConsumers() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 12, 13).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                OsrsTileFlags.BLOCK_MAP_SQUARE | OsrsTileFlags.REMOVE_ROOFS,
                List.of()));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(loaded.regionId(), loaded));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        var address = WorldTileAddress.of(10 * 64 + 12, 20 * 64 + 13, 1);
        var snapshot = scene.collision().get(address);
        assertEquals(CollisionFlag.BLOCK_WALK | CollisionFlag.ROOF, snapshot.rawFlags());
        assertTrue(snapshot.floorBlocked());
        assertTrue(snapshot.roof());
        assertEquals(address, WorldTileAddress.of(
                10 * 64 + snapshot.coordinate().x(),
                20 * 64 + snapshot.coordinate().y(),
                snapshot.coordinate().plane()));
    }

    @Test
    void stitchesEastNeighborBeforeBuildingSharedGeometry() {
        WorldDocument westDocument = new WorldDocument(64, 64, 4);
        WorldDocument eastDocument = new WorldDocument(64, 64, 4);
        westDocument.tile(0, 63, 4).restore(new TileSnapshot(
                10, 20, 30, 40, 0, 0, 0, 0, 0, List.of()));
        eastDocument.tile(0, 0, 4).restore(new TileSnapshot(
                200, 201, 202, 203, 0, 0, 0, 0, 0, List.of()));
        eastDocument.tile(0, 0, 5).restore(new TileSnapshot(
                203, 204, 205, 206, 0, 0, 0, 0, 0, List.of()));
        WorldRegion west = new WorldRegion(10, 20, westDocument);
        WorldRegion east = new WorldRegion(11, 20, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(west.regionId(), west, east.regionId(), east));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        assertEquals(20, westDocument.tile(0, 63, 4).snapshot().southEastHeight());
        assertEquals(30, westDocument.tile(0, 63, 4).snapshot().northEastHeight());
        assertEquals(scene.window().region(10, 20).orElseThrow().document()
                        .tile(0, 63, 4).snapshot().southEastHeight(),
                eastDocument.tile(0, 0, 4).snapshot().southWestHeight());
        assertEquals(scene.window().region(10, 20).orElseThrow().document()
                        .tile(0, 63, 4).snapshot().northEastHeight(),
                eastDocument.tile(0, 0, 5).snapshot().southWestHeight());
    }
}
