package com.rspsi.api;

/**
 * A loaded rectangle of the world, as {@code net.runelite.api.WorldView}.
 *
 * <p>Scene coordinates run from {@code 0} to {@code getSizeX()-1} relative to
 * {@link #getBaseX()}/{@link #getBaseY()}.</p>
 */
public interface WorldView {
    Scene getScene();

    /** World X of scene tile 0. */
    int getBaseX();

    /** World Y of scene tile 0. */
    int getBaseY();

    int getSizeX();

    int getSizeY();

    /** {@code -1} for the top-level view, as in RuneLite. */
    int getId();

    boolean isInstance();

    /** Region ids that supplied this view. */
    int[] getMapRegions();

    /**
     * Vertex heights {@code [plane][sceneX][sceneY]} of size
     * {@code 4 x (sizeX+1) x (sizeY+1)}, indexed by authored level (bridge
     * shifting does not move heights, matching the client). A fresh copy.
     */
    int[][][] getTileHeights();

    /** Authored tile settings {@code [plane][sceneX][sceneY]}. A fresh copy. */
    byte[][][] getTileSettings();

    /** Studio extra: one vertex height without copying {@link #getTileHeights()}. */
    int getTileHeight(int level, int vertexX, int vertexY);

    /** Studio extra: one tile's settings without copying {@link #getTileSettings()}. */
    int getTileSetting(int level, int sceneX, int sceneY);
}
