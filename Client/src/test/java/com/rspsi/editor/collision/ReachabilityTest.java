package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachabilityTest {
    @Test
    void reachesTheBorderOfAnObjectFootprint() {
        CollisionMap map = new CollisionMap(6, 6, 1);
        List<TileCoordinate> route = Reachability.routeTo(map,
                new TileCoordinate(0, 0, 2), new TileCoordinate(0, 3, 2), 2, 2, 100);

        assertFalse(route.isEmpty());
        TileCoordinate last = route.get(route.size() - 1);
        assertTrue(last.x() == 2 || last.x() == 5 || last.y() == 1 || last.y() == 4);
    }

    @Test
    void rejectsAnObjectWhoseEntireBorderIsCutOff() {
        CollisionMap map = new CollisionMap(5, 5, 1);
        TileCoordinate object = new TileCoordinate(0, 2, 2);
        for (TileCoordinate blocked : List.of(
                new TileCoordinate(0, 1, 2), new TileCoordinate(0, 3, 2),
                new TileCoordinate(0, 2, 1), new TileCoordinate(0, 2, 3))) {
            map.add(blocked, CollisionFlag.BLOCK_WALK);
        }

        assertFalse(Reachability.canReach(map, new TileCoordinate(0, 0, 2), object, 1, 1, 100));
    }

    @Test
    void treatsAStartInsideTheFootprintAsAlreadyReached() {
        CollisionMap map = new CollisionMap(4, 4, 1);
        assertTrue(Reachability.canReach(map, new TileCoordinate(0, 2, 2),
                new TileCoordinate(0, 1, 1), 2, 2, 1));
    }

    @Test
    void routesForTheRequestedActorSize() {
        CollisionMap map = new CollisionMap(7, 3, 1);

        List<TileCoordinate> route = Reachability.routeTo(map,
                new TileCoordinate(0, 0, 1), new TileCoordinate(0, 5, 1),
                1, 1, 100, 2, false);

        assertFalse(route.isEmpty());
        TileCoordinate last = route.get(route.size() - 1);
        assertTrue(last.x() == 4 || last.y() == 0 || last.y() == 2);
    }
}
