package com.rspsi.editor.collision;

/** OSRS movement/projectile collision bits kept independent of server types. */
public final class CollisionFlag {
    private CollisionFlag() { }

    public static final int WALL_NORTH_WEST = 0x1;
    public static final int WALL_NORTH = 0x2;
    public static final int WALL_NORTH_EAST = 0x4;
    public static final int WALL_EAST = 0x8;
    public static final int WALL_SOUTH_EAST = 0x10;
    public static final int WALL_SOUTH = 0x20;
    public static final int WALL_SOUTH_WEST = 0x40;
    public static final int WALL_WEST = 0x80;
    public static final int LOC = 0x100;

    public static final int WALL_NORTH_WEST_PROJECTILE = 0x200;
    public static final int WALL_NORTH_PROJECTILE = 0x400;
    public static final int WALL_NORTH_EAST_PROJECTILE = 0x800;
    public static final int WALL_EAST_PROJECTILE = 0x1000;
    public static final int WALL_SOUTH_EAST_PROJECTILE = 0x2000;
    public static final int WALL_SOUTH_PROJECTILE = 0x4000;
    public static final int WALL_SOUTH_WEST_PROJECTILE = 0x8000;
    public static final int WALL_WEST_PROJECTILE = 0x10000;
    public static final int LOC_PROJECTILE = 0x20000;
    public static final int GROUND_DECOR = 0x40000;
    public static final int BLOCK_WALK = 0x200000;

    private static final int FLOOR_BLOCKED = BLOCK_WALK | GROUND_DECOR;

    public static final int BLOCK_WEST = WALL_EAST | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_EAST = WALL_WEST | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_SOUTH = WALL_NORTH | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_NORTH = WALL_SOUTH | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_SOUTH_WEST = WALL_NORTH | WALL_NORTH_EAST | WALL_EAST | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_SOUTH_EAST = WALL_NORTH_WEST | WALL_NORTH | WALL_WEST | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_NORTH_WEST = WALL_EAST | WALL_SOUTH_EAST | WALL_SOUTH | LOC | FLOOR_BLOCKED;
    public static final int BLOCK_NORTH_EAST = WALL_SOUTH | WALL_SOUTH_WEST | WALL_WEST | LOC | FLOOR_BLOCKED;
}
