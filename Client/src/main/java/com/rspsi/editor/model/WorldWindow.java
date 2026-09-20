package com.rspsi.editor.model;

import java.util.Objects;

/**
 * World-space placement of a canonical document window.
 *
 * <p>{@link LocalTile} stays local to a document while {@link WorldTile}
 * represents absolute OSRS coordinates. This type is the only conversion
 * boundary between those spaces.</p>
 */
public record WorldWindow(int originX, int originY, int width, int length) {
    public WorldWindow {
        if (width <= 0 || length <= 0) {
            throw new IllegalArgumentException("World window dimensions must be positive");
        }
    }

    public boolean contains(LocalTile coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return coordinate.x() >= 0 && coordinate.x() < width
                && coordinate.y() >= 0 && coordinate.y() < length;
    }

    public boolean contains(WorldTile coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return coordinate.x() >= originX && coordinate.x() < originX + width
                && coordinate.y() >= originY && coordinate.y() < originY + length;
    }

    public WorldTile toWorld(LocalTile coordinate) {
        requireContains(coordinate);
        return new WorldTile(coordinate.plane(),
                originX + coordinate.x(), originY + coordinate.y());
    }

    public java.util.Optional<LocalTile> tryToLocal(WorldTile coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (!contains(coordinate)) return java.util.Optional.empty();
        return java.util.Optional.of(new LocalTile(coordinate.plane(),
                coordinate.x() - originX, coordinate.y() - originY));
    }

    public LocalTile toLocal(WorldTile coordinate) {
        return tryToLocal(coordinate).orElseThrow(() ->
                new IndexOutOfBoundsException("World tile outside window: " + coordinate));
    }

    public int worldX(LocalTile coordinate) {
        requireContains(coordinate);
        return originX + coordinate.x();
    }

    public int worldY(LocalTile coordinate) {
        requireContains(coordinate);
        return originY + coordinate.y();
    }

    /**
     * Compatibility bridge for older local-coordinate code. New code should
     * use LocalTile explicitly.
     */
    @Deprecated
    public boolean contains(TileCoordinate coordinate) {
        return coordinate != null && contains(LocalTile.from(coordinate));
    }

    @Deprecated
    public int worldX(TileCoordinate coordinate) {
        return worldX(LocalTile.from(coordinate));
    }

    @Deprecated
    public int worldY(TileCoordinate coordinate) {
        return worldY(LocalTile.from(coordinate));
    }

    private void requireContains(LocalTile coordinate) {
        if (!contains(coordinate)) {
            throw new IndexOutOfBoundsException("Tile outside world window: " + coordinate);
        }
    }
}
