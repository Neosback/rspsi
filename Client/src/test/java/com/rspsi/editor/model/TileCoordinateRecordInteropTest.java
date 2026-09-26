package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileCoordinateRecordInteropTest {

    @Test
    void localAndWorldTilesRemainRealJvmRecords() {
        assertTrue(LocalTile.class.isRecord());
        assertTrue(WorldTile.class.isRecord());
    }

    @Test
    void localTilePreservesRecordSurfaceAndLegacyCoordinateBridge() {
        LocalTile local = new LocalTile(2, 15, 31);

        assertEquals(2, local.plane());
        assertEquals(15, local.x());
        assertEquals(31, local.y());
        assertEquals(new LocalTile(2, 15, 31), local);
        assertTrue(local.toString().startsWith("LocalTile["));

        TileCoordinate legacy = local.coordinate();
        assertEquals(2, legacy.plane());
        assertEquals(15, legacy.x());
        assertEquals(31, legacy.y());
        assertEquals(local, LocalTile.from(legacy));
    }

    @Test
    void localTilePreservesValidationAndStaticFromContract() {
        assertThrows(IllegalArgumentException.class, () -> new LocalTile(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LocalTile(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LocalTile(0, 0, -1));
        assertThrows(NullPointerException.class, () -> LocalTile.from(null));
    }

    @Test
    void worldTilePreservesRecordSurfaceAndAddressConversion() {
        WorldTile world = new WorldTile(1, 3205, 3210);

        assertEquals(1, world.plane());
        assertEquals(3205, world.x());
        assertEquals(3210, world.y());
        assertEquals(new WorldTile(1, 3205, 3210), world);
        assertTrue(world.toString().startsWith("WorldTile["));

        WorldTileAddress address = world.address();
        assertEquals(3205, address.worldX());
        assertEquals(3210, address.worldY());
        assertEquals(1, address.plane());
        assertEquals(3205 >> 6, address.regionX());
        assertEquals(3210 >> 6, address.regionY());
        assertEquals(3205 & 63, address.regionLocalX());
        assertEquals(3210 & 63, address.regionLocalY());
        assertEquals(3205 >> 3, address.chunkX());
        assertEquals(3210 >> 3, address.chunkY());
    }

    @Test
    void worldTileRejectsNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new WorldTile(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTile(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTile(0, 0, -1));
    }
}
