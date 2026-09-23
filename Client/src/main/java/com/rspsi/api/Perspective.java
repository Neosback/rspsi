package com.rspsi.api;

import com.rspsi.api.coords.LocalPoint;

import java.util.Objects;

/** Scene geometry helpers, as the camera-independent part of {@code net.runelite.api.Perspective}. */
public final class Perspective {
    public static final int LOCAL_COORD_BITS = 7;
    public static final int LOCAL_TILE_SIZE = 1 << LOCAL_COORD_BITS;
    public static final int LOCAL_HALF_TILE_SIZE = LOCAL_TILE_SIZE / 2;

    /** Tile settings bit that links a column below a bridge ({@code Constants.TILE_FLAG_BRIDGE}). */
    public static final int TILE_FLAG_BRIDGE = 2;

    private Perspective() {
    }

    /**
     * Ground height at a local point, bit-for-bit as RuneLite's
     * {@code Perspective.getTileHeight}: integer bilinear interpolation of the
     * tile's four vertex heights, reading the level above when that column is
     * a bridge. Returns 0 outside the view.
     */
    public static int getTileHeight(WorldView view, LocalPoint point, int plane) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(point, "point");
        int sceneX = point.getSceneX();
        int sceneY = point.getSceneY();
        if (sceneX < 0 || sceneY < 0 || sceneX >= view.getSizeX() || sceneY >= view.getSizeY()) {
            return 0;
        }
        int level = plane;
        if (plane < 3 && (view.getTileSetting(1, sceneX, sceneY) & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE) {
            level = plane + 1;
        }
        int x = point.getX() & (LOCAL_TILE_SIZE - 1);
        int y = point.getY() & (LOCAL_TILE_SIZE - 1);
        int south = x * view.getTileHeight(level, sceneX + 1, sceneY)
                + (LOCAL_TILE_SIZE - x) * view.getTileHeight(level, sceneX, sceneY) >> LOCAL_COORD_BITS;
        int north = view.getTileHeight(level, sceneX, sceneY + 1) * (LOCAL_TILE_SIZE - x)
                + x * view.getTileHeight(level, sceneX + 1, sceneY + 1) >> LOCAL_COORD_BITS;
        return (LOCAL_TILE_SIZE - y) * south + y * north >> LOCAL_COORD_BITS;
    }
}
