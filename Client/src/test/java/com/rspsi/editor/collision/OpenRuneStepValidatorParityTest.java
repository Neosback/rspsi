package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential vectors for OpenRune-Server's
 * {@code engine/routefinder/StepValidator.kt}.
 *
 * <p>The reference below intentionally stays test-only. The production API
 * remains RSPSi-owned and neutral, while this test locks the donor's swept
 * edge sampling for every direction, size, and route-blocker strategy.</p>
 */
class OpenRuneStepValidatorParityTest {
    @Test
    void sweptStepSamplingMatchesOpenRuneForSizesOneThroughFour() {
        Random random = new Random(0x4f52554e455f5350L);
        TileCoordinate origin = new TileCoordinate(0, 6, 6);

        for (int sample = 0; sample < 96; sample++) {
            CollisionMap map = randomMap(random);
            for (boolean routeBlockers : new boolean[]{false, true}) {
                for (int size = 1; size <= 4; size++) {
                    for (CollisionDirection direction : CollisionDirection.values()) {
                        boolean expected = openRuneCanTravel(map, origin, direction, size,
                                routeBlockers);
                        boolean actual = map.canTravel(origin, direction, size, routeBlockers);
                        assertEquals(expected, actual,
                                "sample=" + sample + ", route=" + routeBlockers
                                        + ", size=" + size + ", direction=" + direction);
                    }
                }
            }
        }
    }

    private static CollisionMap randomMap(Random random) {
        CollisionMap map = new CollisionMap(16, 16, 1);
        for (int x = 0; x < map.width(); x++) {
            for (int y = 0; y < map.length(); y++) {
                map.set(new TileCoordinate(0, x, y), random.nextInt());
            }
        }
        return map;
    }

    private static boolean openRuneCanTravel(CollisionMap map, TileCoordinate from,
                                             CollisionDirection direction, int size,
                                             boolean route) {
        int x = from.x();
        int y = from.y();
        return switch (direction) {
            case NORTH -> north(map, from, x, y, size, route);
            case SOUTH -> south(map, from, x, y, size, route);
            case EAST -> east(map, from, x, y, size, route);
            case WEST -> west(map, from, x, y, size, route);
            case SOUTH_WEST -> southWest(map, from, x, y, size, route);
            case NORTH_WEST -> northWest(map, from, x, y, size, route);
            case SOUTH_EAST -> southEast(map, from, x, y, size, route);
            case NORTH_EAST -> northEast(map, from, x, y, size, route);
        };
    }

    private static boolean north(CollisionMap map, TileCoordinate from, int x, int y,
                                 int size, boolean route) {
        if (size == 1) return clear(map, from, x, y + 1, simple(CollisionDirection.NORTH, route));
        if (!clear(map, from, x, y + size, corner(CollisionFlag.BLOCK_NORTH_WEST,
                CollisionFlag.BLOCK_NORTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!clear(map, from, x + size - 1, y + size, corner(CollisionFlag.BLOCK_NORTH_EAST,
                CollisionFlag.BLOCK_NORTH_EAST_ROUTE_BLOCKER, route))) return false;
        return middle(map, from, y + size, x + 1, x + size - 1,
                corner(CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                        CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route), true);
    }

    private static boolean south(CollisionMap map, TileCoordinate from, int x, int y,
                                 int size, boolean route) {
        if (size == 1) return clear(map, from, x, y - 1, simple(CollisionDirection.SOUTH, route));
        if (!clear(map, from, x, y - 1, corner(CollisionFlag.BLOCK_SOUTH_WEST,
                CollisionFlag.BLOCK_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!clear(map, from, x + size - 1, y - 1, corner(CollisionFlag.BLOCK_SOUTH_EAST,
                CollisionFlag.BLOCK_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
        return middle(map, from, y - 1, x + 1, x + size - 1,
                corner(CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                        CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route), true);
    }

    private static boolean east(CollisionMap map, TileCoordinate from, int x, int y,
                                int size, boolean route) {
        if (size == 1) return clear(map, from, x + 1, y, simple(CollisionDirection.EAST, route));
        if (!clear(map, from, x + size, y, corner(CollisionFlag.BLOCK_SOUTH_EAST,
                CollisionFlag.BLOCK_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
        if (!clear(map, from, x + size, y + size - 1, corner(CollisionFlag.BLOCK_NORTH_EAST,
                CollisionFlag.BLOCK_NORTH_EAST_ROUTE_BLOCKER, route))) return false;
        return middle(map, from, x + size, y + 1, y + size - 1,
                corner(CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                        CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route), false);
    }

    private static boolean west(CollisionMap map, TileCoordinate from, int x, int y,
                                int size, boolean route) {
        if (size == 1) return clear(map, from, x - 1, y, simple(CollisionDirection.WEST, route));
        if (!clear(map, from, x - 1, y, corner(CollisionFlag.BLOCK_SOUTH_WEST,
                CollisionFlag.BLOCK_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!clear(map, from, x - 1, y + size - 1, corner(CollisionFlag.BLOCK_NORTH_WEST,
                CollisionFlag.BLOCK_NORTH_WEST_ROUTE_BLOCKER, route))) return false;
        return middle(map, from, x - 1, y + 1, y + size - 1,
                corner(CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                        CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route), false);
    }

    private static boolean southWest(CollisionMap map, TileCoordinate from, int x, int y,
                                     int size, boolean route) {
        if (size == 1) {
            return clear(map, from, x - 1, y - 1, simple(CollisionDirection.SOUTH_WEST, route))
                    && clear(map, from, x - 1, y, simple(CollisionDirection.WEST, route))
                    && clear(map, from, x, y - 1, simple(CollisionDirection.SOUTH, route));
        }
        if (size == 2) {
            return clear(map, from, x - 1, y, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route))
                    && clear(map, from, x - 1, y - 1, composite(
                    CollisionFlag.BLOCK_SOUTH_WEST,
                    CollisionFlag.BLOCK_SOUTH_WEST_ROUTE_BLOCKER, route))
                    && clear(map, from, x, y - 1, composite(
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route));
        }
        if (!clear(map, from, x - 1, y - 1, simple(CollisionDirection.SOUTH_WEST, route))) return false;
        for (int offset = 1; offset < size; offset++) {
            if (!clear(map, from, x - 1, y + offset - 1, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
            if (!clear(map, from, x + offset - 1, y - 1, composite(
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route))) return false;
        }
        return true;
    }

    private static boolean northWest(CollisionMap map, TileCoordinate from, int x, int y,
                                     int size, boolean route) {
        if (size == 1) {
            return clear(map, from, x - 1, y + 1, simple(CollisionDirection.NORTH_WEST, route))
                    && clear(map, from, x - 1, y, simple(CollisionDirection.WEST, route))
                    && clear(map, from, x, y + 1, simple(CollisionDirection.NORTH, route));
        }
        if (size == 2) {
            return clear(map, from, x - 1, y + 1, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route))
                    && clear(map, from, x - 1, y + 2, composite(
                    CollisionFlag.BLOCK_NORTH_WEST,
                    CollisionFlag.BLOCK_NORTH_WEST_ROUTE_BLOCKER, route))
                    && clear(map, from, x, y + 2, composite(
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route));
        }
        if (!clear(map, from, x - 1, y + size, simple(CollisionDirection.NORTH_WEST, route))) return false;
        for (int offset = 1; offset < size; offset++) {
            if (!clear(map, from, x - 1, y + offset, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
            if (!clear(map, from, x + offset - 1, y + size, composite(
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route))) return false;
        }
        return true;
    }

    private static boolean southEast(CollisionMap map, TileCoordinate from, int x, int y,
                                     int size, boolean route) {
        if (size == 1) {
            return clear(map, from, x + 1, y - 1, simple(CollisionDirection.SOUTH_EAST, route))
                    && clear(map, from, x + 1, y, simple(CollisionDirection.EAST, route))
                    && clear(map, from, x, y - 1, simple(CollisionDirection.SOUTH, route));
        }
        if (size == 2) {
            return clear(map, from, x + 1, y - 1, composite(
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route))
                    && clear(map, from, x + 2, y - 1, composite(
                    CollisionFlag.BLOCK_SOUTH_EAST,
                    CollisionFlag.BLOCK_SOUTH_EAST_ROUTE_BLOCKER, route))
                    && clear(map, from, x + 2, y, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route));
        }
        if (!clear(map, from, x + size, y - 1, simple(CollisionDirection.SOUTH_EAST, route))) return false;
        for (int offset = 1; offset < size; offset++) {
            if (!clear(map, from, x + size, y + offset - 1, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
            if (!clear(map, from, x + offset, y - 1, composite(
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route))) return false;
        }
        return true;
    }

    private static boolean northEast(CollisionMap map, TileCoordinate from, int x, int y,
                                     int size, boolean route) {
        if (size == 1) {
            return clear(map, from, x + 1, y + 1, simple(CollisionDirection.NORTH_EAST, route))
                    && clear(map, from, x + 1, y, simple(CollisionDirection.EAST, route))
                    && clear(map, from, x, y + 1, simple(CollisionDirection.NORTH, route));
        }
        if (size == 2) {
            return clear(map, from, x + 1, y + 2, composite(
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route))
                    && clear(map, from, x + 2, y + 2, composite(
                    CollisionFlag.BLOCK_NORTH_EAST,
                    CollisionFlag.BLOCK_NORTH_EAST_ROUTE_BLOCKER, route))
                    && clear(map, from, x + 2, y + 1, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route));
        }
        if (!clear(map, from, x + size, y + size, simple(CollisionDirection.NORTH_EAST, route))) return false;
        for (int offset = 1; offset < size; offset++) {
            if (!clear(map, from, x + offset, y + size, composite(
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                    CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route))) return false;
            if (!clear(map, from, x + size, y + offset, composite(
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                    CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
        }
        return true;
    }

    private static boolean middle(CollisionMap map, TileCoordinate from, int fixed,
                                  int start, int end, int mask, boolean fixedX) {
        for (int value = start; value < end; value++) {
            int x = fixedX ? value : fixed;
            int y = fixedX ? fixed : value;
            if (!clear(map, from, x, y, mask)) return false;
        }
        return true;
    }

    private static int simple(CollisionDirection direction, boolean route) {
        return route ? direction.routeMask() : direction.movementMask();
    }

    private static int corner(int normal, int routeMask, boolean route) {
        return route ? routeMask : normal;
    }

    private static int composite(int normal, int routeMask, boolean route) {
        return route ? routeMask : normal;
    }

    private static boolean clear(CollisionMap map, TileCoordinate from, int x, int y, int mask) {
        return map.contains(new TileCoordinate(from.plane(), x, y))
                && (map.flags(from.plane(), x, y) & mask) == 0;
    }
}
