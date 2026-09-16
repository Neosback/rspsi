package com.rspsi.editor.collision;

/** Tile movement directions used by collision previews and route tools. */
public enum CollisionDirection {
    NORTH(0, 1, CollisionFlag.BLOCK_NORTH, CollisionFlag.WALL_SOUTH_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    SOUTH(0, -1, CollisionFlag.BLOCK_SOUTH, CollisionFlag.WALL_NORTH_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    EAST(1, 0, CollisionFlag.BLOCK_EAST, CollisionFlag.WALL_WEST_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    WEST(-1, 0, CollisionFlag.BLOCK_WEST, CollisionFlag.WALL_EAST_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    NORTH_EAST(1, 1, CollisionFlag.BLOCK_NORTH_EAST,
            CollisionFlag.WALL_SOUTH_WEST_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    NORTH_WEST(-1, 1, CollisionFlag.BLOCK_NORTH_WEST,
            CollisionFlag.WALL_SOUTH_EAST_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    SOUTH_EAST(1, -1, CollisionFlag.BLOCK_SOUTH_EAST,
            CollisionFlag.WALL_NORTH_WEST_PROJECTILE | CollisionFlag.LOC_PROJECTILE),
    SOUTH_WEST(-1, -1, CollisionFlag.BLOCK_SOUTH_WEST,
            CollisionFlag.WALL_NORTH_EAST_PROJECTILE | CollisionFlag.LOC_PROJECTILE);

    private final int deltaX;
    private final int deltaY;
    private final int movementMask;
    private final int projectileMask;

    CollisionDirection(int deltaX, int deltaY, int movementMask, int projectileMask) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.movementMask = movementMask;
        this.projectileMask = projectileMask;
    }

    public int deltaX() { return deltaX; }
    public int deltaY() { return deltaY; }
    public int movementMask() { return movementMask; }
    public int projectileMask() { return projectileMask; }

    public boolean diagonal() {
        return deltaX != 0 && deltaY != 0;
    }
}
