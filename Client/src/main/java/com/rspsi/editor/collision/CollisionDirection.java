package com.rspsi.editor.collision;

/** Tile movement directions used by collision previews and route tools. */
public enum CollisionDirection {
    NORTH(0, 1, CollisionFlag.BLOCK_NORTH),
    SOUTH(0, -1, CollisionFlag.BLOCK_SOUTH),
    EAST(1, 0, CollisionFlag.BLOCK_EAST),
    WEST(-1, 0, CollisionFlag.BLOCK_WEST),
    NORTH_EAST(1, 1, CollisionFlag.BLOCK_NORTH_EAST),
    NORTH_WEST(-1, 1, CollisionFlag.BLOCK_NORTH_WEST),
    SOUTH_EAST(1, -1, CollisionFlag.BLOCK_SOUTH_EAST),
    SOUTH_WEST(-1, -1, CollisionFlag.BLOCK_SOUTH_WEST);

    private final int deltaX;
    private final int deltaY;
    private final int movementMask;

    CollisionDirection(int deltaX, int deltaY, int movementMask) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.movementMask = movementMask;
    }

    public int deltaX() { return deltaX; }
    public int deltaY() { return deltaY; }
    public int movementMask() { return movementMask; }
}
