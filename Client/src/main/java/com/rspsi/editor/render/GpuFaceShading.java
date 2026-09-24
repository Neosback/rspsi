package com.rspsi.editor.render;

/**
 * Renderer-neutral face-local shading metadata.
 *
 * <p>These values are constant across a source triangle even though the
 * historic {@link GpuSceneVertex} contract broadcasts them to each vertex.
 * Keeping them explicit lets native backends move face/material state without
 * coupling it to stable geometry or per-vertex color/light data.</p>
 */
public record GpuFaceShading(
        int alpha,
        int renderType,
        int priority
) {
    public GpuFaceShading {
        if (alpha < 0 || alpha > 255 || renderType < 0
                || priority < 0 || priority > 255) {
            throw new IllegalArgumentException("Invalid GPU face shading metadata");
        }
    }

    public static GpuFaceShading from(GpuSceneVertex vertex) {
        java.util.Objects.requireNonNull(vertex, "vertex");
        return new GpuFaceShading(vertex.alpha(), vertex.renderType(), vertex.priority());
    }
}
