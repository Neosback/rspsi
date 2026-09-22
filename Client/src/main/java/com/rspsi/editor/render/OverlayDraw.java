package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTile;

/**
 * Rich 3D and 2D drawing vocabulary for editor tools and plugin overlays.
 *
 * <p>All colors are specified as 32-bit packed RGBA (0xRRGGBBAA).
 * World coordinates follow the canonical OSRS engine system where
 * (X, Z) correspond to the tile grid (each tile is 128 units across)
 * and Y is height (negative is upward).</p>
 */
public interface OverlayDraw {
    /**
     * Outlines the specified tile perimeter with default color (white/cyan).
     */
    void tileOutline(WorldTile tile);

    /**
     * Outlines the specified tile perimeter with a custom RGBA color.
     */
    default void tileOutline(WorldTile tile, int colorRgba) {
        tileOutline(tile);
    }

    /**
     * Fills the specified tile quad with a semi-transparent RGBA color.
     */
    default void tileFilled(WorldTile tile, int colorRgba) {
    }

    /**
     * Fills the specified tile quad with the default selection fill color/opacity.
     */
    default void tileFilled(WorldTile tile) {
    }

    /**
     * Draws a 3D line between two world-space coordinates with custom thickness.
     */
    default void line(float x1, float y1, float z1, float x2, float y2, float z2, int colorRgba, float thickness) {
    }

    /**
     * Draws a 3D line between two world-space coordinates with default 1px thickness.
     */
    default void line(float x1, float y1, float z1, float x2, float y2, float z2, int colorRgba) {
        line(x1, y1, z1, x2, y2, z2, colorRgba, 1.0f);
    }

    /**
     * Draws an axis-aligned 3D bounding box.
     */
    default void box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int colorRgba, boolean filled) {
    }

    /**
     * Draws a 3D horizontal circle on the X-Z plane centered at (cx, cy, cz).
     */
    default void circle(float cx, float cy, float cz, float radius, int colorRgba, float thickness) {
    }

    /**
     * Draws a 3D horizontal circle on the X-Z plane with default 1px thickness.
     */
    default void circle(float cx, float cy, float cz, float radius, int colorRgba) {
        circle(cx, cy, cz, radius, colorRgba, 1.0f);
    }

    /**
     * Draws a 3D directional arrow from start to end.
     */
    default void arrow(float startX, float startY, float startZ, float endX, float endY, float endZ, int colorRgba) {
    }

    /**
     * Draws a text badge anchored at a 3D world position.
     */
    default void worldLabel(String text, float worldX, float worldY, float worldZ, int textColorRgba, int bgColorRgba) {
    }

    /**
     * Draws a text badge anchored at a 3D world position with default colors.
     */
    default void worldLabel(String text, float worldX, float worldY, float worldZ) {
        worldLabel(text, worldX, worldY, worldZ, 0xFFFFFFFF, 0xCC1E293B);
    }

    /**
     * Draws a 2D screen-space label badge.
     */
    default void screenLabel(String text, float screenX, float screenY, int textColorRgba, int bgColorRgba) {
    }

    /**
     * Draws a 2D screen-space label badge with default colors.
     */
    default void screenLabel(String text, float screenX, float screenY) {
        screenLabel(text, screenX, screenY, 0xFFFFFFFF, 0xCC1E293B);
    }

    /**
     * Draws a text badge anchored at a tile coordinate in 3D world space.
     */
    default void tileLabel(String text, WorldTile tile) {
        if (tile != null) {
            worldLabel(text, tile.x() * 128.0f + 64.0f, 0.0f, tile.y() * 128.0f + 64.0f);
        }
    }

    /**
     * Backwards-compatible label method. If coordinates appear to be tile coordinates (0..128),
     * projects to world space; otherwise renders as screen coordinates.
     */
    default void label(String text, float x, float y) {
        if (x >= 0.0f && x <= 128.0f && y >= 0.0f && y <= 128.0f) {
            worldLabel(text, x * 128.0f + 64.0f, 0.0f, y * 128.0f + 64.0f);
        } else {
            screenLabel(text, x, y);
        }
    }

    /**
     * Draws a 2D rectangle in viewport screen coordinates.
     */
    default void screenRect(float minX, float minY, float maxX, float maxY, int colorRgba, boolean filled) {
    }

    /**
     * Draws a convex polygon already in 2D screen-space coordinates (e.g. the
     * output of {@link #modelHull}). Points are absolute screen coordinates,
     * with a default 2px outline stroke.
     */
    default void screenPolygon(java.util.List<float[]> screenPoints, int colorRgba, boolean filled) {
        screenPolygon(screenPoints, colorRgba, filled, 2.0f);
    }

    /**
     * Same as {@link #screenPolygon(java.util.List, int, boolean)}, with an explicit outline thickness.
     */
    default void screenPolygon(java.util.List<float[]> screenPoints, int colorRgba, boolean filled, float thickness) {
    }

    /**
     * Projects a set of 3D world-space points - typically a model's own
     * vertices - to screen space, computes their 2D convex hull, and draws
     * it. This hugs the object's actual silhouette instead of a generic
     * bounding box, the same technique RuneLite uses to highlight game
     * objects (project every vertex, then wrap a hull around the resulting
     * screen-space point cloud). Points behind the camera are dropped before
     * the hull is computed; nothing is drawn if fewer than 3 points remain
     * visible.
     */
    default void modelHull(java.util.List<float[]> worldPoints, int colorRgba, boolean filled) {
        modelHull(worldPoints, colorRgba, filled, 2.0f);
    }

    /**
     * Same as {@link #modelHull(java.util.List, int, boolean)}, with an explicit outline thickness -
     * e.g. to draw a second, wider, lower-alpha pass behind the crisp outline for a
     * softer "painted edge" look instead of a thin wireframe line.
     */
    default void modelHull(java.util.List<float[]> worldPoints, int colorRgba, boolean filled, float thickness) {
        java.util.List<float[]> screen = new java.util.ArrayList<>(worldPoints.size());
        for (float[] p : worldPoints) {
            ScreenPoint sp = worldToScreen(p[0], p[1], p[2]);
            if (sp.visible()) screen.add(new float[]{sp.x(), sp.y()});
        }
        java.util.List<float[]> hull = ConvexHull2D.compute(screen);
        if (hull.size() >= 3) screenPolygon(hull, colorRgba, filled, thickness);
    }

    /** Draws only the hull outline (no fill) at the given thickness - see {@link #modelHull}. */
    default void modelHullOutline(java.util.List<float[]> worldPoints, int colorRgba, float thickness) {
        modelHull(worldPoints, colorRgba, false, thickness);
    }

    /**
     * Projects a 3D world position into 2D viewport coordinates.
     * Returns a {@link ScreenPoint} with {@code visible=false} if behind the camera.
     */
    default ScreenPoint worldToScreen(float worldX, float worldY, float worldZ) {
        return ScreenPoint.hidden();
    }
}
