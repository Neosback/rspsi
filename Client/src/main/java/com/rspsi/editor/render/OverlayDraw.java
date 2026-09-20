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
     * Projects a 3D world position into 2D viewport coordinates.
     * Returns a {@link ScreenPoint} with {@code visible=false} if behind the camera.
     */
    default ScreenPoint worldToScreen(float worldX, float worldY, float worldZ) {
        return ScreenPoint.hidden();
    }
}
