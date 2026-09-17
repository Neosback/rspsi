package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/** Immutable result that a frontend can render without knowing collision internals. */
public record RoutePreview(
        RoutePreviewMode mode,
        TileCoordinate start,
        TileCoordinate target,
        List<TileCoordinate> path,
        boolean successful,
        String message
) {
    public RoutePreview {
        mode = Objects.requireNonNull(mode, "mode");
        start = Objects.requireNonNull(start, "start");
        target = Objects.requireNonNull(target, "target");
        path = List.copyOf(path == null ? List.of() : path);
        message = Objects.requireNonNull(message, "message");
    }

    public static RoutePreview failure(RoutePreviewMode mode, TileCoordinate start,
                                      TileCoordinate target, String message) {
        return new RoutePreview(mode, start, target, List.of(), false, message);
    }
}
