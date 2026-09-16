package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionTileSnapshotTest {
    @Test
    void decodesRawFlagsForCollisionOverlays() {
        CollisionMap map = new CollisionMap(3, 3, 1);
        TileCoordinate coordinate = new TileCoordinate(0, 1, 1);
        map.add(coordinate, CollisionFlag.BLOCK_NORTH | CollisionFlag.LOC
                | CollisionFlag.WALL_NORTH_ROUTE_BLOCKER
                | CollisionFlag.WALL_EAST_PROJECTILE | CollisionFlag.ROOF);

        CollisionTileSnapshot snapshot = CollisionTileSnapshot.from(map, coordinate);

        assertEquals(CollisionFlag.BLOCK_NORTH | CollisionFlag.LOC
                        | CollisionFlag.WALL_NORTH_ROUTE_BLOCKER
                        | CollisionFlag.WALL_EAST_PROJECTILE | CollisionFlag.ROOF,
                snapshot.rawFlags());
        assertTrue(snapshot.movementBlocked().contains(CollisionDirection.NORTH));
        assertTrue(snapshot.routeBlocked().contains(CollisionDirection.NORTH));
        assertTrue(snapshot.projectileBlocked().contains(CollisionDirection.WEST));
        assertTrue(snapshot.floorBlocked());
        assertTrue(snapshot.objectBlocked());
        assertTrue(snapshot.roof());
    }
}
