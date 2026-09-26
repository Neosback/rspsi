package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeBoundsRecordInteropTest {

    @Test
    void bridgeLinkRemainsJvmRecordAndPreservesAdjacencyInvariant() {
        TileCoordinate lower = new TileCoordinate(1, 20, 30);
        TileCoordinate upper = new TileCoordinate(2, 20, 30);
        BridgeLink link = new BridgeLink(upper, lower);

        assertTrue(BridgeLink.class.isRecord());
        assertSame(upper, link.upper());
        assertSame(lower, link.lower());
        assertEquals(new BridgeLink(upper, lower), link);
        assertTrue(link.toString().startsWith("BridgeLink["));

        assertThrows(IllegalArgumentException.class,
                () -> new BridgeLink(new TileCoordinate(3, 20, 30), lower));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeLink(new TileCoordinate(2, 21, 30), lower));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeLink(new TileCoordinate(2, 20, 31), lower));
        assertThrows(NullPointerException.class, () -> new BridgeLink(null, lower));
        assertThrows(NullPointerException.class, () -> new BridgeLink(upper, null));
    }

    @Test
    void tileBoundsPreservesInclusiveGeometry() {
        TileBounds bounds = new TileBounds(10, 20, 14, 23);

        assertTrue(TileBounds.class.isRecord());
        assertEquals(10, bounds.minX());
        assertEquals(20, bounds.minY());
        assertEquals(14, bounds.maxX());
        assertEquals(23, bounds.maxY());
        assertEquals(5, bounds.width());
        assertEquals(4, bounds.height());

        assertTrue(bounds.contains(10, 20));
        assertTrue(bounds.contains(14, 23));
        assertTrue(bounds.contains(12, 21));
        assertFalse(bounds.contains(9, 20));
        assertFalse(bounds.contains(15, 23));
        assertFalse(bounds.contains(14, 24));
        assertEquals(new TileBounds(10, 20, 14, 23), bounds);
        assertTrue(bounds.toString().startsWith("TileBounds["));
    }

    @Test
    void tileBoundsPreservesValidationContract() {
        assertThrows(IllegalArgumentException.class, () -> new TileBounds(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TileBounds(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TileBounds(2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TileBounds(0, 2, 1, 1));
    }
}
