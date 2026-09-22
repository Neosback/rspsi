package com.rspsi.editor.render;

/**
 * RuneScape scene packets use a single, front-facing triangle winding.
 *
 * <p>The software reference rasterizer expresses projected coordinates with
 * Y increasing down the image and accepts a negative edge-function value.
 * That edge expression is itself the negative of the conventional 2D signed
 * area. Converting from the client's Y-down screen coordinates to OpenGL's
 * Y-up window coordinates flips the conventional signed area once, so the
 * two sign inversions cancel: a client-front face remains negative in
 * OpenGL's conventional window-space signed-area convention and is therefore
 * clockwise.</p>
 *
 * <p>Native winding remains an optimization hint only. The Phase 0 OpenGL
 * baseline intentionally disables culling until asymmetric cache-model and
 * shaped-tile fixtures have verified the full projection path.</p>
 */
public final class BackfacePolicy {
    private static final float DEGENERATE_EPSILON = 0.0001f;

    private BackfacePolicy() {
    }

    /** Returns true when the client's projected edge-function marks a face front-facing. */
    public static boolean isFrontFacingSoftware(float edgeFunction) {
        return edgeFunction < -DEGENERATE_EPSILON;
    }

    /**
     * OpenGL front-face winding equivalent to the software client's accepted
     * edge-function sign after accounting for both sign conventions.
     */
    public static NativeWinding nativeWinding() {
        return NativeWinding.CLOCKWISE;
    }

    /**
     * Converts the client's edge-function value to conventional OpenGL
     * window-space signed area. The value keeps the same sign because the
     * client's edge expression and the Y-axis conversion each contribute one
     * sign inversion.
     */
    public static float nativeWindowArea(float softwareEdgeFunction) {
        return softwareEdgeFunction;
    }

    public enum NativeWinding {
        CLOCKWISE
    }
}
