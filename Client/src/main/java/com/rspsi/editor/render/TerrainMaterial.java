package com.rspsi.editor.render;

/**
 * Neutral terrain material inputs for a scene renderer.
 *
 * <p>IDs and texture selection remain semantic data. A renderer may turn the
 * colors into GPU resources, but it must not need to know cache archive
 * layouts or definition-library types.</p>
 */
public record TerrainMaterial(
        int underlayId,
        int overlayId,
        int textureId,
        int underlayRgb,
        int overlayRgb
) {
    public TerrainMaterial {
        if (underlayId < 0 || overlayId < 0 || textureId < -1) {
            throw new IllegalArgumentException("Terrain material IDs must be non-negative");
        }
        if ((underlayRgb & 0xFF000000) != 0 || (overlayRgb & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Terrain material colors must be RGB values");
        }
    }
}
