package com.rspsi.editor.model;

/**
 * Tile coordinate local to one {@link WorldDocument}.
 *
 * <p>This type must never be used for OSRS absolute world coordinates. The
 * document boundary accepts LocalTile so an absolute viewport pick cannot be
 * passed into a 64x64 document accidentally.</p>
 */
public record LocalTile(int plane, int x, int y) {
    public LocalTile {
        if (plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("Local tile coordinates cannot be negative");
        }
    }

    /** Compatibility bridge while older command/selection contracts migrate. */
    public TileCoordinate coordinate() {
        return new TileCoordinate(plane, x, y);
    }

    public static LocalTile from(TileCoordinate coordinate) {
        if (coordinate == null) throw new NullPointerException("coordinate");
        return new LocalTile(coordinate.plane(), coordinate.x(), coordinate.y());
    }
}
