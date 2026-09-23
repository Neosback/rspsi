package com.rspsi.api;

/** Tiles of one {@link WorldView}, as {@code net.runelite.api.Scene}. */
public interface Scene {
    /** {@code [scenePlane][sceneX][sceneY]}, {@code null} where the client has no tile. A fresh array. */
    Tile[][][] getTiles();

    /** Studio convenience: one tile, or {@code null} outside the scene or where none exists. */
    Tile getTile(int plane, int sceneX, int sceneY);

    int getBaseX();

    int getBaseY();

    /** Lowest scene level rendered. */
    int getMinLevel();

    /** Authored underlay id + 1 per tile, 0 for none (RuneLite encoding). */
    short[][][] getUnderlayIds();

    /** Authored overlay id + 1 per tile, 0 for none (RuneLite encoding). */
    short[][][] getOverlayIds();

    /** Authored overlay shape per tile. */
    byte[][][] getTileShapes();

    /** Vertex heights; see {@link WorldView#getTileHeights()}. */
    int[][][] getTileHeights();
}
