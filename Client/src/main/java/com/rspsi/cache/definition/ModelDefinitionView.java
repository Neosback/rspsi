package com.rspsi.cache.definition;

/** Stable model metadata used by asset browsers and scene preparation. */
public record ModelDefinitionView(
        int id,
        int vertexCount,
        int triangleCount,
        int textureTriangleCount,
        int renderPriority
) {
    public ModelDefinitionView {
        if (id < 0 || vertexCount < 0 || triangleCount < 0 || textureTriangleCount < 0) {
            throw new IllegalArgumentException("Model counts and identity cannot be negative");
        }
    }
}
