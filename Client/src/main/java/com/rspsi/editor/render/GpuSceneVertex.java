package com.rspsi.editor.render;

/** One world-space vertex in the backend-neutral GPU upload plan. */
public record GpuSceneVertex(
        float x,
        float y,
        float z,
        float u,
        float v,
        int encodedColor,
        GpuColorEncoding colorEncoding,
        int renderType,
        int normalX,
        int normalY,
        int normalZ,
        int normalMagnitude,
        int textureId,
        int alpha,
        int priority
) {
    public GpuSceneVertex {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("GPU scene vertex values must be finite");
        }
        colorEncoding = java.util.Objects.requireNonNull(colorEncoding, "colorEncoding");
        if (normalMagnitude < 0 || textureId < -1 || alpha < 0 || alpha > 255
                || priority < 0 || priority > 255 || renderType < 0) {
            throw new IllegalArgumentException("Invalid GPU scene vertex material data");
        }
    }
}
