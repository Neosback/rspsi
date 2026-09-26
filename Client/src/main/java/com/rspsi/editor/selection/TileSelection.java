package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

public record TileSelection(TileCoordinate coordinate) implements Selection {
    public TileSelection {
        Objects.requireNonNull(coordinate, "coordinate");
    }
}
