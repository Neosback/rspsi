package com.rspsi.editor.render;

/** Full-precision transformed model vertex with optional normal and UV data. */
public record ModelVertex(
        int x,
        int y,
        int z,
        int normalX,
        int normalY,
        int normalZ,
        int normalMagnitude,
        float u,
        float v
) {
    /** Compatibility constructor before explicit normal magnitudes were exposed. */
    public ModelVertex(int x, int y, int z, int normalX, int normalY, int normalZ,
                       float u, float v) {
        this(x, y, z, normalX, normalY, normalZ, 1, u, v);
    }

    public ModelVertex {
        if (normalMagnitude < 0 || !Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("Model UV coordinates must be finite");
        }
    }
}
