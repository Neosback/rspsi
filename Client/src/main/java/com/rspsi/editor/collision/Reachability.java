package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded editor preview for reaching an object footprint. */
public final class Reachability {
    private Reachability() { }

    public static boolean canReach(CollisionMap map, TileCoordinate start, TileCoordinate object,
                                   int width, int length, int maxVisited) {
        return canReach(map, start, object, width, length, maxVisited, false);
    }

    /** Tests reachability with optional OpenRune route-blocker semantics. */
    public static boolean canReach(CollisionMap map, TileCoordinate start, TileCoordinate object,
                                   int width, int length, int maxVisited,
                                   boolean useRouteBlockers) {
        return !routeTo(map, start, object, width, length, maxVisited, 1,
                useRouteBlockers).isEmpty();
    }

    /** Tests reachability for an actor of the supplied size using normal movement masks. */
    public static boolean canReach(CollisionMap map, TileCoordinate start, TileCoordinate object,
                                   int width, int length, int maxVisited, int actorSize) {
        return canReach(map, start, object, width, length, maxVisited, actorSize, false);
    }

    /** Tests reachability for a square actor using OpenRune footprint checks. */
    public static boolean canReach(CollisionMap map, TileCoordinate start, TileCoordinate object,
                                   int width, int length, int maxVisited, int actorSize,
                                   boolean useRouteBlockers) {
        return !routeTo(map, start, object, width, length, maxVisited, actorSize,
                useRouteBlockers).isEmpty();
    }

    /**
     * Finds a route to any walkable tile bordering the object footprint. If the
     * start is already inside the footprint, the route contains that start.
     */
    public static List<TileCoordinate> routeTo(CollisionMap map, TileCoordinate start,
                                               TileCoordinate object, int width, int length,
                                               int maxVisited) {
        return routeTo(map, start, object, width, length, maxVisited, false);
    }

    /** Finds a route to an object using an explicitly selected blocker layer. */
    public static List<TileCoordinate> routeTo(CollisionMap map, TileCoordinate start,
                                               TileCoordinate object, int width, int length,
                                               int maxVisited, boolean useRouteBlockers) {
        return routeTo(map, start, object, width, length, maxVisited, 1,
                useRouteBlockers);
    }

    /** Finds a route for an actor of the supplied size using normal movement masks. */
    public static List<TileCoordinate> routeTo(CollisionMap map, TileCoordinate start,
                                               TileCoordinate object, int width, int length,
                                               int maxVisited, int actorSize) {
        return routeTo(map, start, object, width, length, maxVisited, actorSize, false);
    }

    /** Finds a route to an object for a square actor of the supplied size. */
    public static List<TileCoordinate> routeTo(CollisionMap map, TileCoordinate start,
                                               TileCoordinate object, int width, int length,
                                               int maxVisited, int actorSize,
                                               boolean useRouteBlockers) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(object, "object");
        if (width <= 0 || length <= 0) throw new IllegalArgumentException("Object footprint must be positive");
        if (actorSize <= 0) throw new IllegalArgumentException("Actor size must be positive");
        if (object.plane() != start.plane()) return List.of();
        if (inside(start, object, width, length)) return List.of(start);

        for (TileCoordinate candidate : border(object, width, length)) {
            if (candidate.x() < 0 || candidate.x() >= map.width()
                    || candidate.y() < 0 || candidate.y() >= map.length()) continue;
            List<TileCoordinate> route = RouteFinder.find(map, start, candidate, maxVisited,
                    actorSize, useRouteBlockers);
            if (!route.isEmpty()) return route;
        }
        return List.of();
    }

    private static boolean inside(TileCoordinate tile, TileCoordinate object, int width, int length) {
        return tile.x() >= object.x() && tile.x() < object.x() + width
                && tile.y() >= object.y() && tile.y() < object.y() + length;
    }

    private static Set<TileCoordinate> border(TileCoordinate object, int width, int length) {
        Set<TileCoordinate> result = new LinkedHashSet<>();
        for (int x = object.x(); x < object.x() + width; x++) {
            addIfNonNegative(result, object.plane(), x, object.y() - 1);
            addIfNonNegative(result, object.plane(), x, object.y() + length);
        }
        for (int y = object.y(); y < object.y() + length; y++) {
            addIfNonNegative(result, object.plane(), object.x() - 1, y);
            addIfNonNegative(result, object.plane(), object.x() + width, y);
        }
        return result;
    }

    private static void addIfNonNegative(Set<TileCoordinate> result, int plane, int x, int y) {
        if (x >= 0 && y >= 0) {
            result.add(new TileCoordinate(plane, x, y));
        }
    }
}
