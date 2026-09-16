package com.rspsi.editor.model;

import java.util.Objects;

/**
 * World-space placement of a canonical document window.
 *
 * <p>{@link TileCoordinate} stays local to a document. This type supplies the
 * world origin needed by inspectors, region-boundary checks, and future
 * multi-region views without putting cache coordinates into a tile.</p>
 */
public record WorldWindow(int originX, int originY, int width, int length) {
    public WorldWindow {
        if (width <= 0 || length <= 0) {
            throw new IllegalArgumentException("World window dimensions must be positive");
        }
    }

    public boolean contains(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return coordinate.x() < width && coordinate.y() < length;
    }

    public int worldX(TileCoordinate coordinate) {
        requireContains(coordinate);
        return originX + coordinate.x();
    }

    public int worldY(TileCoordinate coordinate) {
        requireContains(coordinate);
        return originY + coordinate.y();
    }

    private void requireContains(TileCoordinate coordinate) {
        if (!contains(coordinate)) {
            throw new IndexOutOfBoundsException("Tile outside world window: " + coordinate);
        }
    }
}
