package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class LegacyTileCoordinateInteropTest {

    @Test
    void remainsDeprecatedJvmRecordWithJavaComponentAccessors() {
        TileCoordinate coordinate = new TileCoordinate(2, 15, 31);

        assertTrue(TileCoordinate.class.isRecord());
        assertEquals(2, coordinate.plane());
        assertEquals(15, coordinate.x());
        assertEquals(31, coordinate.y());
        assertEquals(new TileCoordinate(2, 15, 31), coordinate);
        assertEquals(coordinate.hashCode(), new TileCoordinate(2, 15, 31).hashCode());
        assertEquals("TileCoordinate[plane=2, x=15, y=31]", coordinate.toString());
        assertTrue(TileCoordinate.class.isAnnotationPresent(Deprecated.class));
    }

    @Test
    void preservesNonNegativeValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TileCoordinate(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TileCoordinate(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TileCoordinate(0, 0, -1));
        assertDoesNotThrow(() -> new TileCoordinate(0, 0, 0));
    }

    @Test
    void remainsCompatibleWithLocalTileBridge() {
        TileCoordinate legacy = new TileCoordinate(3, 7, 9);

        LocalTile local = LocalTile.from(legacy);

        assertEquals(new LocalTile(3, 7, 9), local);
        assertEquals(legacy, local.coordinate());
    }
}
