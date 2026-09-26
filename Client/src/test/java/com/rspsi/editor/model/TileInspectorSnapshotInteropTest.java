package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileInspectorSnapshotInteropTest {

    @Test
    void remainsJvmRecordAndPreservesJavaComponentAccessors() {
        WorldTileAddress address = WorldTileAddress.of(3205, 3210, 1);
        TileSnapshot tile = tileWithFlags(18);
        TileInspectorSnapshot snapshot = new TileInspectorSnapshot(address, tile, true, false);

        assertTrue(TileInspectorSnapshot.class.isRecord());
        assertSame(address, snapshot.address());
        assertSame(tile, snapshot.tile());
        assertTrue(snapshot.bridge());
        assertFalse(snapshot.roofRelated());
        assertEquals(new TileInspectorSnapshot(address, tile, true, false), snapshot);
        assertTrue(snapshot.toString().startsWith("TileInspectorSnapshot["));
    }

    @Test
    void rawFlagsPreservesTileFlagProjection() {
        TileInspectorSnapshot snapshot = new TileInspectorSnapshot(
                WorldTileAddress.of(3200, 3200, 0),
                tileWithFlags(0x12),
                false,
                true);

        assertEquals(0x12, snapshot.rawFlags());
    }

    @Test
    void preservesNullRejectionForRequiredPayloads() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TileSnapshot tile = tileWithFlags(0);

        assertThrows(NullPointerException.class,
                () -> new TileInspectorSnapshot(null, tile, false, false));
        assertThrows(NullPointerException.class,
                () -> new TileInspectorSnapshot(address, null, false, false));
    }

    private static TileSnapshot tileWithFlags(int flags) {
        return new TileSnapshot(
                0, 0, 0, 0,
                0, 0,
                0, 0,
                flags,
                List.of());
    }
}
