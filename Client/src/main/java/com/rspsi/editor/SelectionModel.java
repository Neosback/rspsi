package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Editor selection state kept separate from scene rendering state. */
public final class SelectionModel {
    private final Set<TileCoordinate> tiles = new LinkedHashSet<>();

    public void select(TileCoordinate coordinate) {
        tiles.add(coordinate);
    }

    public void deselect(TileCoordinate coordinate) {
        tiles.remove(coordinate);
    }

    public void clear() {
        tiles.clear();
    }

    public boolean contains(TileCoordinate coordinate) {
        return tiles.contains(coordinate);
    }

    public Set<TileCoordinate> tiles() {
        return Collections.unmodifiableSet(tiles);
    }
}
