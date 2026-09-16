package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;

/** Mutable canonical tile state. Rendering and cache representations adapt to it. */
public final class Tile {
    private final TileCoordinate coordinate;
    private TileSnapshot state;

    Tile(TileCoordinate coordinate) {
        this.coordinate = coordinate;
        this.state = new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    public TileCoordinate coordinate() {
        return coordinate;
    }

    public TileSnapshot snapshot() {
        return state;
    }

    public void restore(TileSnapshot state) {
        this.state = state;
    }

    public List<WorldObject> objects() {
        return new ArrayList<>(state.objects());
    }
}
