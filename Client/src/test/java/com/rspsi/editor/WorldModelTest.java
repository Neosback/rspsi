package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldModelTest {
    @Test
    void modelCreatesFourPlanesAndStableCoordinates() {
        WorldModel world = new WorldModel(8, 9);

        assertEquals(4, world.planes());
        assertEquals(new TileCoordinate(3, 7, 8), world.tile(3, 7, 8).coordinate());
    }

    @Test
    void invalidDimensionsAndCoordinatesFailClearly() {
        assertThrows(IllegalArgumentException.class, () -> new WorldModel(0, 1));
        WorldModel world = new WorldModel(2, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> world.tile(4, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> world.tile(0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new TileCoordinate(-1, 0, 0));
    }
}
