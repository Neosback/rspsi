package com.rspsi.editor.tool;

/** Deterministic tile-grid snapping shared by object tools. */
public final class TileSnapper {
    private TileSnapper() {
    }

    /**
     * Snaps to the nearest grid origin and keeps the result in the document.
     * A grid size of one preserves the unsnapped tile coordinate.
     */
    public static int snap(int coordinate, int gridSize, int limitExclusive) {
        if (coordinate < 0 || limitExclusive <= 0) {
            throw new IllegalArgumentException("Coordinate and limit must be positive");
        }
        if (gridSize < 1) {
            throw new IllegalArgumentException("Grid size must be at least one tile");
        }
        int snapped = ((coordinate + gridSize / 2) / gridSize) * gridSize;
        return Math.min(snapped, limitExclusive - 1);
    }
}
