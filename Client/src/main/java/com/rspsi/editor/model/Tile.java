package com.rspsi.editor.model;

import java.util.List;

/**
 * Mutable canonical tile state. Rendering and cache representations adapt to it.
 *
 * <p>This remains a Java compatibility shell solely to preserve the package-private constructor
 * that restricts canonical tile creation to the model package. Behavioral semantics are
 * centralized in {@link TileSemantics}.</p>
 */
public final class Tile {
    private final TileCoordinate coordinate;
    private TileSnapshot state;
    private TerrainHeightSource heightSource;

    Tile(TileCoordinate coordinate) {
        this.coordinate = coordinate;
        this.heightSource = TileSemantics.initialHeightSource();
        this.state = TileSemantics.initialSnapshot(heightSource);
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
        TileSnapshot safeState = TileSemantics.requireState(state);
        TerrainHeightSource resolved =
                TileSemantics.resolveRestoreHeightSource(this.heightSource, safeState);
        this.heightSource = resolved;
        this.state = TileSemantics.withHeightSource(safeState, resolved);
    }

    /**
     * Restores tile values and explicitly replaces height provenance.
     *
     * <p>Assignment order intentionally matches the historical implementation: the source is
     * validated and assigned before the state null-check.</p>
     */
    public void restore(TileSnapshot state, TerrainHeightSource source) {
        this.heightSource = TileSemantics.requireRestoreSource(source);
        this.state = TileSemantics.withHeightSource(
                TileSemantics.requireState(state),
                source);
    }

    public TerrainHeightSource heightSource() {
        return heightSource;
    }

    public void heightSource(TerrainHeightSource heightSource) {
        this.heightSource = TileSemantics.requireHeightSource(heightSource);
        this.state = TileSemantics.withHeightSource(state, this.heightSource);
    }

    public List<WorldObject> objects() {
        return TileSemantics.copyObjects(state);
    }
}
