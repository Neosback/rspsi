package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileInspectorSnapshot;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldTileAddressTest {
    @Test
    void derivesRegionAndChunkCoordinatesAtBoundaries() {
        WorldTileAddress address = WorldTileAddress.of(3200 + 63, 3200 + 64, 2);

        assertEquals(50, address.regionX());
        assertEquals(51, address.regionY());
        assertEquals((50 << 8) | 51, address.regionId());
        assertEquals(63, address.regionLocalX());
        assertEquals(0, address.regionLocalY());
        assertEquals(7, address.chunkLocalX());
        assertEquals(0, address.chunkLocalY());
        assertEquals(2, address.plane());
    }

    @Test
    void worldWindowConvertsLocalDocumentTile() {
        WorldWindow window = new WorldWindow(3200, 3200, 64, 64);
        TileCoordinate local = new TileCoordinate(1, 63, 8);

        assertTrue(window.contains(local));
        assertEquals(3263, window.worldX(local));
        assertEquals(3208, window.worldY(local));
        assertFalse(window.contains(new TileCoordinate(0, 64, 0)));
    }

    @Test
    void inspectorExposesRawFlagsWithoutMakingColorTheOnlySignal() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0, 1, 2, 1, 0, 0x06, List.of());
        TileInspectorSnapshot snapshot = new TileInspectorSnapshot(
                WorldTileAddress.of(3208, 3218, 0), tile, true, true);

        assertEquals(0x06, snapshot.rawFlags());
        assertTrue(snapshot.bridge());
        assertTrue(snapshot.roofRelated());
    }
}
