package com.rspsi.editor.render;

/**
 * RuneScape scene packets use a single, front-facing triangle winding.
 *
 * <p>The software reference rasterizer expresses projected coordinates with
 * Y increasing down the image and the canonical OSRS world Y axis points
 * down, so the accepted edge-function sign is negative. Native winding is
 * retained as a future optimization hint only;
 * the Phase 0 OpenGL baseline intentionally disables culling until every
 * cache model and shaped-tile family has been parity-verified.</p>
 */
public final class BackfacePolicy {
    private static final float DEGENERATE_EPSILON = 0.0001f;

    private BackfacePolicy() {
    }

    /** Returns true when a software-projected triangle is front-facing. */
    public static boolean isFrontFacingSoftware(float area) {
        return area < -DEGENERATE_EPSILON;
    }

    /**
     * OpenGL front-face winding equivalent to the software client's accepted
     * screen-space sign.
     *
     * <p>The software framebuffer uses a top-left origin (screen Y grows
     * downward), while OpenGL window coordinates use a bottom-left origin
     * (window Y grows upward). That single Y-axis inversion flips triangle
     * orientation: a negative software edge-function area becomes a positive
     * native/window area. Positive window area is counter-clockwise.</p>
     */
    public static NativeWinding nativeWinding() {
        return NativeWinding.COUNTER_CLOCKWISE;
    }

    /** Converts a software screen-space signed area to native window-space. */
    public static float nativeWindowArea(float softwareArea) {
        return -softwareArea;
    }

    public enum NativeWinding {
        COUNTER_CLOCKWISE
    }
}
