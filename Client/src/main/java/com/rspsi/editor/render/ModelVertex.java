package com.rspsi.editor.render;

/** Full-precision transformed model vertex with optional normal and UV data. */
public record ModelVertex(
        int x,
        int y,
        int z,
        int normalX,
        int normalY,
        int normalZ,
        float u,
        float v
) {
    public ModelVertex {
        if (!Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("Model UV coordinates must be finite");
        }
    }
}
