package com.rspsi.editor.render;

/** Shared OSRS face-priority compression and face-bias policy. */
public final class GpuPriority {
    private GpuPriority() {
    }

    /**
     * Compresses the cache's usual 0..11 face priorities into seven bands.
     * This grouping matches RuneLite's priority interleave groups and is used
     * by draw-order concerns (see {@link RsFaceOrderPlanner}); it no longer
     * contributes to depth (see {@link #biasedDepth}).
     */
    public static int band(int priority) {
        if (priority <= 0) return 0;
        if (priority <= 3) return priority;
        if (priority <= 7) return 4 + ((priority - 4) >> 1);
        return 6 + Math.min(1, (priority - 8) >> 1);
    }

    /** Applies only the true client face bias to a positive camera depth for the reference rasterizer. */
    public static float biasedDepth(float depth, float nearPlane, float farPlane, int priority) {
        return biasedDepth(depth, nearPlane, farPlane, priority, 0);
    }

    /** The client's per-face bias step, in world units of view-space depth. */
    public static final int FACE_BIAS_SCALE = 2;

    /**
     * Applies the true client per-face depth bias. The real client subtracts
     * {@code faceBias * 2} from the vertex's view-space depth, in world
     * units, before converting it to a depth-buffer value
     * ({@code Model.java}: {@code faceBias[face] * 2}, then
     * {@code field3037[v] - bias} where {@code field3037} is the raw
     * perspective divisor). The offset is therefore CONSTANT in world space
     * at every distance.
     *
     * <p>This deliberately replaces an earlier clip-space
     * {@code z += bias / 128} formulation copied from RuneLite's GPU shader.
     * That form is distance-scaled: expressed as a world-space separation it
     * shrinks in proportion to depth, so it collapses to nearly nothing when
     * the camera is close to a surface - exactly where coplanar wall
     * decorations need it most. RuneLite can afford it because its own
     * projection differs; against this renderer's projection it produced a
     * pull toward the camera roughly 16x weaker than the client's when
     * zoomed in, leaving flush decals to z-fight with the wall behind them.
     *
     * <p>{@code priority} affects draw order only, never depth. The
     * parameter is retained for call-site stability.</p>
     */
    public static float biasedDepth(float depth, float nearPlane, float farPlane,
                                    int priority, int faceBias) {
        int bias = Math.max(0, Math.min(255, faceBias));
        if (bias == 0) return depth;
        float biased = depth - (float) bias * FACE_BIAS_SCALE;
        return biased > nearPlane ? biased : depth;
    }
}
