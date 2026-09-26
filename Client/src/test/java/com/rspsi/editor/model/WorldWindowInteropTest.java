package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldWindowInteropTest {

    @Test
    void remainsJvmRecordAndPreservesComponentAccessors() {
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);

        assertTrue(WorldWindow.class.isRecord());
        assertEquals(3200, window.originX());
        assertEquals(4800, window.originY());
        assertEquals(64, window.width());
        assertEquals(64, window.length());
        assertEquals(new WorldWindow(3200, 4800, 64, 64), window);
        assertTrue(window.toString().startsWith("WorldWindow["));
    }

    @Test
    void preservesLocalAndWorldContainmentAtInclusiveExclusiveEdges() {
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);

        assertTrue(window.contains(new LocalTile(0, 0, 0)));
        assertTrue(window.contains(new LocalTile(0, 63, 63)));
        assertFalse(window.contains(new LocalTile(0, 64, 63)));
        assertFalse(window.contains(new LocalTile(0, 63, 64)));

        assertTrue(window.contains(new WorldTile(0, 3200, 4800)));
        assertTrue(window.contains(new WorldTile(0, 3263, 4863)));
        assertFalse(window.contains(new WorldTile(0, 3199, 4800)));
        assertFalse(window.contains(new WorldTile(0, 3264, 4800)));
        assertFalse(window.contains(new WorldTile(0, 3200, 4864)));
    }

    @Test
    void convertsBetweenLocalAndWorldCoordinatesWithoutChangingPlane() {
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);
        LocalTile local = new LocalTile(2, 5, 7);

        WorldTile world = window.toWorld(local);

        assertEquals(new WorldTile(2, 3205, 4807), world);
        assertEquals(local, window.toLocal(world));
        assertEquals(local, window.tryToLocal(world).orElseThrow());
        assertEquals(3205, window.worldX(local));
        assertEquals(4807, window.worldY(local));
    }

    @Test
    void outsideCoordinatesPreserveOptionalAndExceptionBehavior() {
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);
        WorldTile outside = new WorldTile(0, 3264, 4800);

        assertTrue(window.tryToLocal(outside).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> window.toLocal(outside));
        assertThrows(IndexOutOfBoundsException.class,
                () -> window.toWorld(new LocalTile(0, 64, 0)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> window.worldX(new LocalTile(0, 64, 0)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> window.worldY(new LocalTile(0, 0, 64)));
    }

    @Test
    void preservesNullContractsAndAllowsNegativeWorldOrigins() {
        WorldWindow window = new WorldWindow(-64, -128, 64, 64);

        assertTrue(window.contains(new WorldTile(0, 0, 0)) == false);
        assertThrows(NullPointerException.class, () -> window.contains((LocalTile) null));
        assertThrows(NullPointerException.class, () -> window.contains((WorldTile) null));
        assertThrows(NullPointerException.class, () -> window.toWorld(null));
        assertThrows(NullPointerException.class, () -> window.tryToLocal(null));
        assertThrows(NullPointerException.class, () -> window.worldX((LocalTile) null));
        assertThrows(NullPointerException.class, () -> window.worldY((LocalTile) null));
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new WorldWindow(0, 0, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new WorldWindow(0, 0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldWindow(0, 0, -1, 64));
        assertThrows(IllegalArgumentException.class, () -> new WorldWindow(0, 0, 64, -1));
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesLegacyTileCoordinateCompatibilityBridges() {
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);
        TileCoordinate legacy = new TileCoordinate(1, 5, 7);

        assertTrue(window.contains(legacy));
        assertFalse(window.contains((TileCoordinate) null));
        assertEquals(3205, window.worldX(legacy));
        assertEquals(4807, window.worldY(legacy));
        assertThrows(NullPointerException.class, () -> window.worldX((TileCoordinate) null));
        assertThrows(NullPointerException.class, () -> window.worldY((TileCoordinate) null));
    }
}
