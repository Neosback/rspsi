package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record TileSetSelection(Set<TileCoordinate> coordinates) implements Selection {
    public TileSetSelection {
        coordinates = Collections.unmodifiableSet(new LinkedHashSet<>(coordinates == null
                ? Set.of() : coordinates));
        if (coordinates.isEmpty()) {
            throw new IllegalArgumentException("A tile selection cannot be empty");
        }
    }
}
