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

    /** World units of view-space depth per packed submission-bias step. */
    public static final float SUBMISSION_BIAS_UNIT = 0.25f;

    /** Packed steps per client face-bias unit: {@code 8 * 0.25 = FACE_BIAS_SCALE}. */
    static final int FACE_BIAS_STEPS = 8;

    /**
     * Packs one face's depth offset for submission: the model's own face bias, exactly
     * as the client applies it ({@code faceBias * 2} world units), plus a quarter unit per
     * face priority.
     *
     * <p>The client's painter's algorithm draws a model's faces in priority order, so a
     * higher-priority face always covers a coplanar lower one (sign artwork over its board,
     * banner crests over the cloth). A depth buffer only sees equal depths there, and
     * rounding between two different triangles decides the winner differently at every
     * camera angle: z-fighting. A quarter-unit view-space step per priority reproduces the
     * client's result at every angle and distance (the float depth target resolves it), yet
     * is far too small to push a face through any real geometry.</p>
     */
    public static int submissionBias(int faceBias, int priority) {
        int steps = Math.max(0, faceBias) * FACE_BIAS_STEPS + Math.max(0, Math.min(11, priority));
        return Math.min(255, steps);
    }

    /**
     * Applies a packed {@link #submissionBias} to a positive camera depth for the reference
     * rasterizer, matching the native vertex shader.
     */
    public static float biasedDepth(float depth, float nearPlane, float farPlane,
                                    int priority, int submissionBias) {
        int bias = Math.max(0, Math.min(255, submissionBias));
        if (bias == 0) return depth;
        float biased = depth - bias * SUBMISSION_BIAS_UNIT;
        return biased > nearPlane ? biased : depth;
    }
}
