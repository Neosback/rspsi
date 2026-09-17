package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/**
 * Small UI-neutral facade for the editor's route, LOS, and object-reach
 * previews. It deliberately returns data rather than drawing or mutating a
 * document, so JavaFX and a future ImGui frontend can share it.
 */
public final class RoutePreviewService {
    private RoutePreviewService() {
    }

    public static RoutePreview evaluate(CollisionMap map, RoutePreviewMode mode,
                                        TileCoordinate start, TileCoordinate target,
                                        int maxVisited, int actorSize,
                                        boolean useRouteBlockers) {
        return evaluate(map, mode, start, target, 1, 1, maxVisited, actorSize,
                useRouteBlockers);
    }

    public static RoutePreview evaluate(CollisionMap map, RoutePreviewMode mode,
                                        TileCoordinate start, TileCoordinate target,
                                        int targetWidth, int targetLength,
                                        int maxVisited, int actorSize,
                                        boolean useRouteBlockers) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        if (targetWidth <= 0 || targetLength <= 0) {
            throw new IllegalArgumentException("Target footprint must be positive");
        }
        if (actorSize <= 0) throw new IllegalArgumentException("Actor size must be positive");
        if (!map.contains(start) || !map.contains(target)) {
            return RoutePreview.failure(mode, start, target, "Start or target is outside the loaded scene");
        }
        if (start.plane() != target.plane()) {
            return RoutePreview.failure(mode, start, target, "Start and target must be on the same plane");
        }

        return switch (mode) {
            case ROUTE -> route(map, mode, start, target, maxVisited, actorSize,
                    useRouteBlockers);
            case LINE_OF_SIGHT -> lineOfSight(map, start, target);
            case REACH -> reach(map, start, target, targetWidth, targetLength,
                    maxVisited, actorSize, useRouteBlockers);
        };
    }

    private static RoutePreview route(CollisionMap map, RoutePreviewMode mode,
                                      TileCoordinate start, TileCoordinate target,
                                      int maxVisited, int actorSize,
                                      boolean useRouteBlockers) {
        List<TileCoordinate> path = RouteFinder.find(map, start, target, maxVisited,
                actorSize, useRouteBlockers);
        return path.isEmpty()
                ? RoutePreview.failure(mode, start, target, "No route found within the search budget")
                : new RoutePreview(mode, start, target, path, true,
                "Route found: " + path.size() + " tiles");
    }

    private static RoutePreview lineOfSight(CollisionMap map, TileCoordinate start,
                                            TileCoordinate target) {
        List<TileCoordinate> path = RouteFinder.line(start, target);
        boolean clear = RouteFinder.hasLineOfSight(map, start, target);
        return new RoutePreview(RoutePreviewMode.LINE_OF_SIGHT, start, target, path, clear,
                clear ? "Line of sight is clear" : "Line of sight is blocked");
    }

    private static RoutePreview reach(CollisionMap map, TileCoordinate start,
                                      TileCoordinate target, int targetWidth,
                                      int targetLength, int maxVisited, int actorSize,
                                      boolean useRouteBlockers) {
        List<TileCoordinate> path = Reachability.routeTo(map, start, target, targetWidth,
                targetLength, maxVisited, actorSize, useRouteBlockers);
        return path.isEmpty()
                ? RoutePreview.failure(RoutePreviewMode.REACH, start, target,
                "Object footprint is not reachable")
                : new RoutePreview(RoutePreviewMode.REACH, start, target, path, true,
                "Reachable from " + path.size() + " tiles");
    }
}
