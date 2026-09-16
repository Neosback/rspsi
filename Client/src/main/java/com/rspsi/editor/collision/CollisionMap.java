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
        return canTravel(from, direction, 1, useRouteBlockers);
    }

    /** Checks one step for an actor of the supplied size using normal movement masks. */
    public boolean canTravel(TileCoordinate from, CollisionDirection direction, int size) {
        return canTravel(from, direction, size, false);
    }

    /**
     * Checks one movement step for a square actor. The edge samples mirror
     * OpenRune-Server's {@code StepValidator}, including swept edges for
     * actors larger than one tile.
     */
    public boolean canTravel(TileCoordinate from, CollisionDirection direction, int size,
                             boolean useRouteBlockers) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");
        if (size <= 0) throw new IllegalArgumentException("Actor size must be positive");
        if (!inside(from.plane(), from.x(), from.y())
                || !inside(from.plane(), from.x() + size - 1, from.y() + size - 1)) {
            return false;
        }
        return switch (direction) {
            case NORTH -> canNorth(from, size, useRouteBlockers);
            case SOUTH -> canSouth(from, size, useRouteBlockers);
            case EAST -> canEast(from, size, useRouteBlockers);
            case WEST -> canWest(from, size, useRouteBlockers);
            case NORTH_EAST -> canNorthEast(from, size, useRouteBlockers);
            case NORTH_WEST -> canNorthWest(from, size, useRouteBlockers);
            case SOUTH_EAST -> canSouthEast(from, size, useRouteBlockers);
            case SOUTH_WEST -> canSouthWest(from, size, useRouteBlockers);
        };
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

    private boolean canNorth(TileCoordinate from, int size, boolean route) {
        int y = from.y() + size;
        if (size == 1) return canAt(from, from.x(), y, simple(CollisionDirection.NORTH, route));
        if (!canAt(from, from.x(), y, composite(CollisionFlag.BLOCK_NORTH_WEST,
                CollisionFlag.BLOCK_NORTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!canAt(from, from.x() + size - 1, y, composite(CollisionFlag.BLOCK_NORTH_EAST,
                CollisionFlag.BLOCK_NORTH_EAST_ROUTE_BLOCKER, route))) return false;
        int mask = composite(CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int x = from.x() + 1; x < from.x() + size - 1; x++) {
            if (!canAt(from, x, y, mask)) return false;
        }
        return true;
    }

    private boolean canSouth(TileCoordinate from, int size, boolean route) {
        int y = from.y() - 1;
        if (size == 1) return canAt(from, from.x(), y, simple(CollisionDirection.SOUTH, route));
        if (!canAt(from, from.x(), y, composite(CollisionFlag.BLOCK_SOUTH_WEST,
                CollisionFlag.BLOCK_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!canAt(from, from.x() + size - 1, y, composite(CollisionFlag.BLOCK_SOUTH_EAST,
                CollisionFlag.BLOCK_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
        int mask = composite(CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int x = from.x() + 1; x < from.x() + size - 1; x++) {
            if (!canAt(from, x, y, mask)) return false;
        }
        return true;
    }

    private boolean canWest(TileCoordinate from, int size, boolean route) {
        int x = from.x() - 1;
        if (size == 1) return canAt(from, x, from.y(), simple(CollisionDirection.WEST, route));
        if (!canAt(from, x, from.y(), composite(CollisionFlag.BLOCK_SOUTH_WEST,
                CollisionFlag.BLOCK_SOUTH_WEST_ROUTE_BLOCKER, route))) return false;
        if (!canAt(from, x, from.y() + size - 1, composite(CollisionFlag.BLOCK_NORTH_WEST,
                CollisionFlag.BLOCK_NORTH_WEST_ROUTE_BLOCKER, route))) return false;
        int mask = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route);
        for (int y = from.y() + 1; y < from.y() + size - 1; y++) {
            if (!canAt(from, x, y, mask)) return false;
        }
        return true;
    }

    private boolean canEast(TileCoordinate from, int size, boolean route) {
        int x = from.x() + size;
        if (size == 1) return canAt(from, x, from.y(), simple(CollisionDirection.EAST, route));
        if (!canAt(from, x, from.y(), composite(CollisionFlag.BLOCK_SOUTH_EAST,
                CollisionFlag.BLOCK_SOUTH_EAST_ROUTE_BLOCKER, route))) return false;
        if (!canAt(from, x, from.y() + size - 1, composite(CollisionFlag.BLOCK_NORTH_EAST,
                CollisionFlag.BLOCK_NORTH_EAST_ROUTE_BLOCKER, route))) return false;
        int mask = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route);
        for (int y = from.y() + 1; y < from.y() + size - 1; y++) {
            if (!canAt(from, x, y, mask)) return false;
        }
        return true;
    }

    private boolean canSouthWest(TileCoordinate from, int size, boolean route) {
        if (size == 1) {
            return canAt(from, from.x() - 1, from.y() - 1, simple(CollisionDirection.SOUTH_WEST, route))
                    && canAt(from, from.x() - 1, from.y(), simple(CollisionDirection.WEST, route))
                    && canAt(from, from.x(), from.y() - 1, simple(CollisionDirection.SOUTH, route));
        }
        if (!canAt(from, from.x() - 1, from.y() - 1, simple(CollisionDirection.SOUTH_WEST, route))) return false;
        int left = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route);
        int bottom = composite(CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int offset = 1; offset < size; offset++) {
            if (!canAt(from, from.x() - 1, from.y() + offset - 1, left)) return false;
            if (!canAt(from, from.x() + offset - 1, from.y() - 1, bottom)) return false;
        }
        return true;
    }

    private boolean canNorthWest(TileCoordinate from, int size, boolean route) {
        if (size == 1) {
            return canAt(from, from.x() - 1, from.y() + 1, simple(CollisionDirection.NORTH_WEST, route))
                    && canAt(from, from.x() - 1, from.y(), simple(CollisionDirection.WEST, route))
                    && canAt(from, from.x(), from.y() + 1, simple(CollisionDirection.NORTH, route));
        }
        if (!canAt(from, from.x() - 1, from.y() + size, simple(CollisionDirection.NORTH_WEST, route))) return false;
        int left = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER, route);
        int top = composite(CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int offset = 1; offset < size; offset++) {
            if (!canAt(from, from.x() - 1, from.y() + offset, left)) return false;
            if (!canAt(from, from.x() + offset - 1, from.y() + size, top)) return false;
        }
        return true;
    }

    private boolean canSouthEast(TileCoordinate from, int size, boolean route) {
        if (size == 1) {
            return canAt(from, from.x() + 1, from.y() - 1, simple(CollisionDirection.SOUTH_EAST, route))
                    && canAt(from, from.x() + 1, from.y(), simple(CollisionDirection.EAST, route))
                    && canAt(from, from.x(), from.y() - 1, simple(CollisionDirection.SOUTH, route));
        }
        if (!canAt(from, from.x() + size, from.y() - 1, simple(CollisionDirection.SOUTH_EAST, route))) return false;
        int right = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route);
        int bottom = composite(CollisionFlag.BLOCK_NORTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int offset = 1; offset < size; offset++) {
            if (!canAt(from, from.x() + size, from.y() + offset - 1, right)) return false;
            if (!canAt(from, from.x() + offset, from.y() - 1, bottom)) return false;
        }
        return true;
    }

    private boolean canNorthEast(TileCoordinate from, int size, boolean route) {
        if (size == 1) {
            return canAt(from, from.x() + 1, from.y() + 1, simple(CollisionDirection.NORTH_EAST, route))
                    && canAt(from, from.x() + 1, from.y(), simple(CollisionDirection.EAST, route))
                    && canAt(from, from.x(), from.y() + 1, simple(CollisionDirection.NORTH, route));
        }
        if (!canAt(from, from.x() + size, from.y() + size, simple(CollisionDirection.NORTH_EAST, route))) return false;
        int right = composite(CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST,
                CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER, route);
        int top = composite(CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST,
                CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER, route);
        for (int offset = 1; offset < size; offset++) {
            if (!canAt(from, from.x() + offset, from.y() + size, top)) return false;
            if (!canAt(from, from.x() + size, from.y() + offset, right)) return false;
        }
        return true;
    }

    private int simple(CollisionDirection direction, boolean route) {
        return route ? direction.routeMask() : direction.movementMask();
    }

    private int composite(int normal, int route, boolean useRouteBlockers) {
        return useRouteBlockers ? route : normal;
    }

    private boolean canAt(TileCoordinate from, int x, int y, int mask) {
        return inside(from.plane(), x, y) && (flags[from.plane()][x][y] & mask) == 0;
    }

    private void check(int plane, int x, int y) {
        if (!inside(plane, x, y)) {
            throw new IndexOutOfBoundsException("Tile outside collision map: " + plane + "," + x + "," + y);
        }
    }
}
