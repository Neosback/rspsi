package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Small, bounded collision-preview routefinder for the editor.
 *
 * <p>This is deliberately a neutral tool-layer service, not a copy of a game
 * server. It provides deterministic previews while OpenRune-Server parity is
 * established. Routes contain both endpoints and are empty when no route is
 * found within the supplied budget.</p>
 */
public final class RouteFinder {
    private static final Set<CollisionDirection> ALL_DIRECTIONS =
            EnumSet.allOf(CollisionDirection.class);

    private RouteFinder() {
    }

    public static List<TileCoordinate> find(CollisionMap map, TileCoordinate start,
                                            TileCoordinate target, int maxVisited) {
        return find(map, start, target, maxVisited, false);
    }

    /**
     * Finds a route with optional OpenRune route-blocker semantics. The
     * default overload intentionally follows the donor's normal strategy,
     * which does not opt into route-blocker flags.
     */
    public static List<TileCoordinate> find(CollisionMap map, TileCoordinate start,
                                            TileCoordinate target, int maxVisited,
                                            boolean useRouteBlockers) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        if (maxVisited <= 0) {
            throw new IllegalArgumentException("Route search budget must be positive");
        }
        if (start.plane() != target.plane()) {
            return List.of();
        }
        if (start.equals(target)) {
            return List.of(start);
        }
        map.flags(start);
        map.flags(target);

        ArrayDeque<TileCoordinate> queue = new ArrayDeque<>();
        Map<TileCoordinate, TileCoordinate> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, null);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < maxVisited) {
            TileCoordinate current = queue.removeFirst();
            for (CollisionDirection direction : ALL_DIRECTIONS) {
                if (!canMove(map, current, direction, useRouteBlockers)) {
                    continue;
                }
                TileCoordinate next = new TileCoordinate(current.plane(),
                        current.x() + direction.deltaX(), current.y() + direction.deltaY());
                if (previous.containsKey(next)) {
                    continue;
                }
                previous.put(next, current);
                if (next.equals(target)) {
                    return reconstruct(previous, target);
                }
                queue.addLast(next);
            }
        }
        return List.of();
    }

    /**
     * Tests a straight projectile line using integer grid traversal. Each
     * crossed tile edge and destination tile must permit the projectile.
     */
    public static boolean hasLineOfSight(CollisionMap map, TileCoordinate start,
                                         TileCoordinate target) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        if (start.plane() != target.plane()) {
            return false;
        }
        map.flags(start);
        map.flags(target);
        int x = start.x();
        int y = start.y();
        int dx = Math.abs(target.x() - x);
        int dy = Math.abs(target.y() - y);
        int sx = Integer.compare(target.x(), x);
        int sy = Integer.compare(target.y(), y);
        int error = dx - dy;
        while (x != target.x() || y != target.y()) {
            int oldX = x;
            int oldY = y;
            int twice = 2 * error;
            if (twice > -dy) {
                error -= dy;
                x += sx;
            }
            if (twice < dx) {
                error += dx;
                y += sy;
            }
            CollisionDirection direction = direction(oldX, oldY, x, y);
            TileCoordinate from = new TileCoordinate(start.plane(), oldX, oldY);
            if (!canProject(map, from, direction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canMove(CollisionMap map, TileCoordinate from,
                                   CollisionDirection direction, boolean useRouteBlockers) {
        if (!map.canTravel(from, direction, useRouteBlockers)) {
            return false;
        }
        if (!direction.diagonal()) {
            return true;
        }
        CollisionDirection horizontal = direction.deltaX() > 0
                ? CollisionDirection.EAST : CollisionDirection.WEST;
        CollisionDirection vertical = direction.deltaY() > 0
                ? CollisionDirection.NORTH : CollisionDirection.SOUTH;
        return map.canTravel(from, horizontal, useRouteBlockers)
                && map.canTravel(from, vertical, useRouteBlockers);
    }

    private static boolean canProject(CollisionMap map, TileCoordinate from,
                                      CollisionDirection direction) {
        if (!map.canProject(from, direction)) {
            return false;
        }
        if (!direction.diagonal()) {
            return true;
        }
        CollisionDirection horizontal = direction.deltaX() > 0
                ? CollisionDirection.EAST : CollisionDirection.WEST;
        CollisionDirection vertical = direction.deltaY() > 0
                ? CollisionDirection.NORTH : CollisionDirection.SOUTH;
        return map.canProject(from, horizontal) && map.canProject(from, vertical);
    }

    private static CollisionDirection direction(int fromX, int fromY, int toX, int toY) {
        for (CollisionDirection direction : ALL_DIRECTIONS) {
            if (direction.deltaX() == toX - fromX && direction.deltaY() == toY - fromY) {
                return direction;
            }
        }
        throw new IllegalStateException("Line traversal skipped more than one tile");
    }

    private static List<TileCoordinate> reconstruct(Map<TileCoordinate, TileCoordinate> previous,
                                                     TileCoordinate target) {
        List<TileCoordinate> route = new ArrayList<>();
        for (TileCoordinate current = target; current != null; current = previous.get(current)) {
            route.add(current);
        }
        Collections.reverse(route);
        return List.copyOf(route);
    }
}
