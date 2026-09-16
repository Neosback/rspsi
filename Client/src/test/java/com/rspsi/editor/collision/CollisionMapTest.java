package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollisionMapTest {
    private final TileCoordinate center = new TileCoordinate(0, 2, 2);

    @Test
    void cardinalAndDiagonalMovementUseDestinationMasks() {
        CollisionMap map = new CollisionMap(5, 5, 1);
        map.add(new TileCoordinate(0, 2, 3), CollisionFlag.BLOCK_NORTH);
        map.add(new TileCoordinate(0, 3, 3), CollisionFlag.BLOCK_NORTH_EAST);

        assertFalse(map.canTravel(center, CollisionDirection.NORTH));
        assertTrue(map.canTravel(center, CollisionDirection.EAST));
        assertFalse(map.canTravel(center, CollisionDirection.NORTH_EAST));
    }

    @Test
    void flagsCanBeAddedRemovedAndInspectedForProjectiles() {
        CollisionMap map = new CollisionMap(5, 5, 1);
        TileCoordinate tile = new TileCoordinate(0, 1, 1);

        map.add(tile, CollisionFlag.WALL_EAST_PROJECTILE | CollisionFlag.LOC);
        assertTrue(map.projectileBlocked(tile, CollisionFlag.WALL_EAST_PROJECTILE));
        assertEquals(CollisionFlag.WALL_EAST_PROJECTILE | CollisionFlag.LOC, map.flags(tile));
        map.remove(tile, CollisionFlag.LOC);
        assertEquals(CollisionFlag.WALL_EAST_PROJECTILE, map.flags(tile));
    }

    @Test
    void mapRejectsOutOfBoundsMutation() {
        CollisionMap map = new CollisionMap(2, 2, 1);
        assertThrows(IndexOutOfBoundsException.class,
                () -> map.flags(new TileCoordinate(0, 2, 0)));
        assertFalse(map.canTravel(new TileCoordinate(0, 0, 0), CollisionDirection.WEST));
    }
}
