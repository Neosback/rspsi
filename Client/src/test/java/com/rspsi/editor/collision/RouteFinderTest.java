package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteFinderTest {
    @Test
    void findsRouteAroundBlockedTile() {
        CollisionMap map = new CollisionMap(5, 3, 1);
        TileCoordinate blocked = new TileCoordinate(0, 2, 1);
        map.add(blocked, CollisionFlag.BLOCK_WALK);

        List<TileCoordinate> route = RouteFinder.find(map,
                new TileCoordinate(0, 0, 1), new TileCoordinate(0, 4, 1), 100);

        assertFalse(route.isEmpty());
        assertEquals(new TileCoordinate(0, 0, 1), route.get(0));
        assertEquals(new TileCoordinate(0, 4, 1), route.get(route.size() - 1));
        assertFalse(route.contains(blocked));
    }

    @Test
    void diagonalCornerCuttingIsRejected() {
        CollisionMap map = new CollisionMap(2, 2, 1);
        map.add(new TileCoordinate(0, 1, 0), CollisionFlag.BLOCK_WALK);
        map.add(new TileCoordinate(0, 0, 1), CollisionFlag.BLOCK_WALK);

        assertTrue(RouteFinder.find(map, new TileCoordinate(0, 0, 0),
                new TileCoordinate(0, 1, 1), 20).isEmpty());
    }

    @Test
    void lineOfSightHonorsProjectileWall() {
        CollisionMap map = new CollisionMap(3, 1, 1);
        map.add(new TileCoordinate(0, 1, 0), CollisionFlag.WALL_WEST_PROJECTILE);

        assertFalse(RouteFinder.hasLineOfSight(map,
                new TileCoordinate(0, 0, 0), new TileCoordinate(0, 2, 0)));
        assertTrue(RouteFinder.hasLineOfSight(new CollisionMap(3, 1, 1),
                new TileCoordinate(0, 0, 0), new TileCoordinate(0, 2, 0)));
    }

    @Test
    void routeBlockersAreOptInLikeOpenRuneNormalStrategy() {
        CollisionMap map = new CollisionMap(3, 1, 1);
        map.add(new TileCoordinate(0, 1, 0), CollisionFlag.LOC_ROUTE_BLOCKER);
        TileCoordinate start = new TileCoordinate(0, 0, 0);
        TileCoordinate target = new TileCoordinate(0, 2, 0);

        assertFalse(RouteFinder.find(map, start, target, 20).isEmpty());
        assertTrue(RouteFinder.find(map, start, target, 20, true).isEmpty());
    }

    @Test
    void reachabilityCanOptIntoRouteBlockers() {
        CollisionMap map = new CollisionMap(5, 1, 1);
        TileCoordinate start = new TileCoordinate(0, 0, 0);
        TileCoordinate object = new TileCoordinate(0, 4, 0);
        map.add(new TileCoordinate(0, 2, 0), CollisionFlag.LOC_ROUTE_BLOCKER);

        assertTrue(Reachability.canReach(map, start, object, 1, 1, 100));
        assertFalse(Reachability.canReach(map, start, object, 1, 1, 100, true));
    }

    @Test
    void reachabilityHandlesObjectsAtMapEdges() {
        CollisionMap map = new CollisionMap(3, 3, 1);

        assertTrue(Reachability.canReach(map,
                new TileCoordinate(0, 1, 0), new TileCoordinate(0, 0, 0), 1, 1, 20));
    }
}
