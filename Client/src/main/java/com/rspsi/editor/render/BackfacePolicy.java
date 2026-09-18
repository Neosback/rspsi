package com.rspsi.editor.render;

/**
 * RuneScape scene packets use a single, front-facing triangle winding.
 *
 * <p>The software reference rasterizer expresses projected coordinates with
 * Y increasing down the image, so the accepted edge-function sign is
 * positive. Native OpenGL consumes the same packets in an upward-facing
 * viewport and therefore uses the equivalent clockwise front-face rule.
 * Keeping that fact here prevents the two backends from silently becoming
 * two-sided renderers with different visibility.</p>
 */
public final class BackfacePolicy {
    private static final float DEGENERATE_EPSILON = 0.0001f;

    private BackfacePolicy() {
    }

    /** Returns true when a software-projected triangle is front-facing. */
    public static boolean isFrontFacingSoftware(float area) {
        return area > DEGENERATE_EPSILON;
    }

    /** OpenGL front-face winding for the native viewport coordinate system. */
    public static NativeWinding nativeWinding() {
        return NativeWinding.CLOCKWISE;
    }

    public enum NativeWinding {
        CLOCKWISE
    }
}
