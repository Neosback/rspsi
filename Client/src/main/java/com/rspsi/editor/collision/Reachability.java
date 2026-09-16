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
        return !routeTo(map, start, object, width, length, maxVisited).isEmpty();
    }

    /**
     * Finds a route to any walkable tile bordering the object footprint. If the
     * start is already inside the footprint, the route contains that start.
     */
    public static List<TileCoordinate> routeTo(CollisionMap map, TileCoordinate start,
                                               TileCoordinate object, int width, int length,
                                               int maxVisited) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(object, "object");
        if (width <= 0 || length <= 0) throw new IllegalArgumentException("Object footprint must be positive");
        if (object.plane() != start.plane()) return List.of();
        if (inside(start, object, width, length)) return List.of(start);

        for (TileCoordinate candidate : border(object, width, length)) {
            if (candidate.x() < 0 || candidate.x() >= map.width()
                    || candidate.y() < 0 || candidate.y() >= map.length()) continue;
            List<TileCoordinate> route = RouteFinder.find(map, start, candidate, maxVisited);
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
            result.add(new TileCoordinate(object.plane(), x, object.y() - 1));
            result.add(new TileCoordinate(object.plane(), x, object.y() + length));
        }
        for (int y = object.y(); y < object.y() + length; y++) {
            result.add(new TileCoordinate(object.plane(), object.x() - 1, y));
            result.add(new TileCoordinate(object.plane(), object.x() + width, y));
        }
        return result;
    }
}
