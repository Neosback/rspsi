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

    /**
     * Applies the true client per-face depth bias (a raw 0..255 value scaled
     * by 1/128), matching RuneLite's real vertex shader
     * ({@code screenPos.z += float(bias) / 128.0;}). RuneLite's priority
     * value affects draw order only, never depth - a face's {@code priority}
     * no longer synthesizes any depth offset here. The parameter is retained
     * for API/call-site stability, not because it still contributes to the
     * result.
     */
    public static float biasedDepth(float depth, float nearPlane, float farPlane,
                                    int priority, int faceBias) {
        // The native renderer uses the conventional OpenGL depth direction
        // (smaller NDC depth is nearer), while RuneLite's GPU shader uses a
        // reversed-Z convention. Its positive face bias therefore maps to a
        // negative NDC offset here.
        float bias = Math.max(0, Math.min(255, faceBias)) / 128.0f;
        if (bias == 0.0f) return depth;
        float a = (farPlane + nearPlane) / (farPlane - nearPlane);
        float b = -2.0f * farPlane * nearPlane / (farPlane - nearPlane);
        float biasedNdc = a + b / depth - bias;
        float result = b / (biasedNdc - a);
        return Float.isFinite(result) && result > nearPlane ? result : depth;
    }
}
