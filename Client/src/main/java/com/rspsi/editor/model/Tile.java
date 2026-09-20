package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable canonical tile state. Rendering and cache representations adapt to it. */
public final class Tile {
    private final TileCoordinate coordinate;
    private TileSnapshot state;
    private TerrainHeightSource heightSource;

    Tile(TileCoordinate coordinate) {
        this.coordinate = coordinate;
        this.heightSource = TerrainHeightSource.unknown();
        this.state = new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), heightSource);
    }

    public TileCoordinate coordinate() {
        return coordinate;
    }

    public TileSnapshot snapshot() {
        return state;
    }

    /**
     * Restores authored tile values without accidentally discarding known
     * height provenance. A snapshot that explicitly carries provenance wins;
     * legacy/unknown snapshots inherit the tile's current source.
     */
    public void restore(TileSnapshot state) {
        Objects.requireNonNull(state, "state");
        TerrainHeightSource incoming = state.heightSource();
        TerrainHeightSource resolved = incoming != null && incoming.known()
                ? incoming : this.heightSource;
        this.heightSource = resolved;
        this.state = state.withHeightSource(resolved);
    }

    /** Restores tile values and explicitly replaces height provenance. */
    public void restore(TileSnapshot state, TerrainHeightSource source) {
        this.heightSource = Objects.requireNonNull(source, "source");
        this.state = Objects.requireNonNull(state, "state").withHeightSource(source);
    }

    public TerrainHeightSource heightSource() {
        return heightSource;
    }

    public void heightSource(TerrainHeightSource heightSource) {
        this.heightSource = Objects.requireNonNull(heightSource, "heightSource");
        this.state = state.withHeightSource(this.heightSource);
    }

    public List<WorldObject> objects() {
        return new ArrayList<>(state.objects());
    }
}
