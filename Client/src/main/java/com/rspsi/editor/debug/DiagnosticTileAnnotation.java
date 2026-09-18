package com.rspsi.editor.debug;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/** Ephemeral scene diagnostic generated from the current immutable snapshot. */
public record DiagnosticTileAnnotation(
        TileCoordinate tile,
        String kind,
        String text,
        DebugColor color
) {
    public DiagnosticTileAnnotation {
        tile = Objects.requireNonNull(tile, "tile");
        kind = text(kind, "diagnostic kind");
        text = text(text, "diagnostic text");
        color = Objects.requireNonNull(color, "color");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
