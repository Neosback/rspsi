package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/**
 * Canonical editor collision storage. The default is unblocked (zero); the
 * routefinder-facing flag vocabulary is kept in {@link CollisionFlag}.
 */
public final class CollisionMap {
    private final int width;
    private final int length;
    private final int planes;
    private final int[][][] flags;

    public CollisionMap(int width, int length, int planes) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Collision dimensions must be positive");
        }
        this.width = width;
        this.length = length;
        this.planes = planes;
        this.flags = new int[planes][width][length];
    }

    public int width() { return width; }
    public int length() { return length; }
    public int planes() { return planes; }

    /** Returns whether a canonical tile is covered by this collision map. */
    public boolean contains(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return inside(coordinate.plane(), coordinate.x(), coordinate.y());
    }

    public int flags(TileCoordinate coordinate) {
        return flags[coordinate.plane()][coordinate.x()][coordinate.y()];
    }

    public int flags(int plane, int x, int y) {
        check(plane, x, y);
        return flags[plane][x][y];
    }

    public void set(TileCoordinate coordinate, int value) {
        Objects.requireNonNull(coordinate, "coordinate");
        check(coordinate.plane(), coordinate.x(), coordinate.y());
        flags[coordinate.plane()][coordinate.x()][coordinate.y()] = value;
    }

    public void add(TileCoordinate coordinate, int value) {
        set(coordinate, flags(coordinate) | value);
    }

    public void remove(TileCoordinate coordinate, int value) {
        set(coordinate, flags(coordinate) & ~value);
    }

    /** Checks the destination tile using the OSRS routefinder mask convention. */
    public boolean canTravel(TileCoordinate from, CollisionDirection direction) {
        return canTravel(from, direction, false);
    }

    /** Checks a destination using OpenRune's optional route-blocker layer. */
    public boolean canTravel(TileCoordinate from, CollisionDirection direction, boolean useRouteBlockers) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");
        int targetX = from.x() + direction.deltaX();
        int targetY = from.y() + direction.deltaY();
        if (!inside(from.plane(), targetX, targetY)) {
            return false;
        }
        int mask = useRouteBlockers ? direction.routeMask() : direction.movementMask();
        return (flags[from.plane()][targetX][targetY] & mask) == 0;
    }

    public boolean projectileBlocked(TileCoordinate coordinate, int projectileMask) {
        return (flags(coordinate) & projectileMask) != 0;
    }

    /** Checks one projectile step using the same destination-mask convention. */
    public boolean canProject(TileCoordinate from, CollisionDirection direction) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");
        int targetX = from.x() + direction.deltaX();
        int targetY = from.y() + direction.deltaY();
        if (!inside(from.plane(), targetX, targetY)) {
            return false;
        }
        return (flags[from.plane()][targetX][targetY] & direction.projectileMask()) == 0;
    }

    private boolean inside(int plane, int x, int y) {
        return plane >= 0 && plane < planes && x >= 0 && x < width && y >= 0 && y < length;
    }

    private void check(int plane, int x, int y) {
        if (!inside(plane, x, y)) {
            throw new IndexOutOfBoundsException("Tile outside collision map: " + plane + "," + x + "," + y);
        }
    }
}
