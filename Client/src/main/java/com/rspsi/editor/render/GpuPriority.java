package com.rspsi.editor.render;

/** Shared OSRS face-priority compression and depth-bias policy. */
public final class GpuPriority {
    private static final float LAYER_EPSILON = 0.015f;
    private static final float TOP_PRIORITY_EXTRA = 0.01f;

    private GpuPriority() {
    }

    /** Compresses the cache's usual 0..11 face priorities into seven GPU bands. */
    public static int band(int priority) {
        if (priority <= 0) return 0;
        if (priority <= 3) return priority;
        if (priority <= 7) return 4 + ((priority - 4) >> 1);
        return 6 + Math.min(1, (priority - 8) >> 1);
    }

    /** Returns the normalized-device-coordinate bias used by the client-like path. */
    public static float ndcBias(int priority) {
        int band = band(priority);
        return band == 7
                ? band * LAYER_EPSILON + TOP_PRIORITY_EXTRA
                : band * LAYER_EPSILON;
    }

    /** Applies the NDC bias to a positive camera depth for the reference rasterizer. */
    public static float biasedDepth(float depth, float nearPlane, float farPlane, int priority) {
        return biasedDepth(depth, nearPlane, farPlane, priority, 0);
    }

    /** Applies client face bias in addition to the editor's priority ordering. */
    public static float biasedDepth(float depth, float nearPlane, float farPlane,
                                    int priority, int faceBias) {
        float bias = ndcBias(priority);
        // The native renderer uses the conventional OpenGL depth direction
        // (smaller NDC depth is nearer), while RuneLite's GPU shader uses a
        // reversed-Z convention. Its positive face bias therefore maps to a
        // negative NDC offset here.
        bias += Math.max(0, Math.min(255, faceBias)) / 128.0f;
        if (bias == 0.0f) return depth;
        float a = (farPlane + nearPlane) / (farPlane - nearPlane);
        float b = -2.0f * farPlane * nearPlane / (farPlane - nearPlane);
        float biasedNdc = a + b / depth - bias;
        float result = b / (biasedNdc - a);
        return Float.isFinite(result) && result > nearPlane ? result : depth;
    }
}
