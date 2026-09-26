package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileBounds;

import java.util.Objects;

public record TileAreaSelection(int plane, TileBounds bounds) implements Selection {
    public TileAreaSelection {
        if (plane < 0) {
            throw new IllegalArgumentException("Selection plane cannot be negative");
        }
        Objects.requireNonNull(bounds, "bounds");
    }
}
