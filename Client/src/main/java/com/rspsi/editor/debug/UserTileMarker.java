package com.rspsi.editor.debug;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/** Persisted user annotation; it is not a diagnostic or scene mutation. */
public record UserTileMarker(
        String id,
        TileCoordinate tile,
        String label,
        DebugColor color,
        boolean visible
) {
    public UserTileMarker {
        id = text(id, "marker id");
        tile = Objects.requireNonNull(tile, "tile");
        label = text(label, "marker label");
        color = Objects.requireNonNull(color, "color");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
