package com.rspsi.editor.render;

/** Renderer-neutral terrain triangle and its material metadata. */
public record TerrainRenderFace(
        int a,
        int b,
        int c,
        int material,
        int textureId,
        int alpha,
        int priority
) {
    /** Terrain alpha is opacity: 255 is fully opaque and 0 is a hole. */
    public TerrainRenderFace {
        if (a < 0 || b < 0 || c < 0 || material < 0 || material > 1) {
            throw new IllegalArgumentException("Invalid terrain face indices or material");
        }
        if (textureId < -1 || alpha < 0 || alpha > 255 || priority < 0 || priority > 255) {
            throw new IllegalArgumentException("Invalid terrain face material values");
        }
    }
}
