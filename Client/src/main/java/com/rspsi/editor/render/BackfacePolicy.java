package com.rspsi.editor.render;

/**
 * RuneScape scene packets use a single, front-facing triangle winding.
 *
 * <p>RuneLite-melxin's {@code Model.draw0} computes the same projected
 * edge-function sign used by the software reference renderer. The client
 * stores {@code edge <= 0} in its culled-face flag and only draws faces for
 * which that flag is false, so a visible client face has a strictly positive
 * edge value.</p>
 *
 * <p>That client edge expression is the negative of conventional signed area
 * in the client's Y-down screen coordinates. OpenGL window coordinates use a
 * Y-up axis, which flips conventional signed area once. The two sign changes
 * therefore make a positive client edge correspond to positive conventional
 * OpenGL window area, i.e. counter-clockwise winding.</p>
 *
 * <p>Native winding remains an optimization hint only. The Phase 0 OpenGL
 * baseline intentionally disables culling until asymmetric cache-model and
 * shaped-tile fixtures have verified the full projection path.</p>
 */
public final class BackfacePolicy {
    private static final float DEGENERATE_EPSILON = 0.0001f;

    private BackfacePolicy() {
    }

    /** Returns true when the client's projected edge-function marks a face visible. */
    public static boolean isFrontFacingSoftware(float edgeFunction) {
        return edgeFunction > DEGENERATE_EPSILON;
    }

    /** OpenGL front-face winding equivalent to the client's visible-face sign. */
    public static NativeWinding nativeWinding() {
        return NativeWinding.COUNTER_CLOCKWISE;
    }

    /**
     * Converts the client/software edge-function sign to conventional OpenGL
     * window-space signed area. They have the same sign after the Y-axis
     * conversion described in the class contract.
     */
    public static float nativeWindowArea(float softwareEdgeFunction) {
        return softwareEdgeFunction;
    }

    /**
     * Native viewport validation mode. Production/default rendering remains
     * two-sided until the real-cache acceptance fixtures are complete.
     */
    public enum NativeCullingMode {
        TWO_SIDED,
        CLIENT_FRONT,
        REVERSED_DEBUG
    }

    public enum NativeWinding {
        COUNTER_CLOCKWISE
    }
}
