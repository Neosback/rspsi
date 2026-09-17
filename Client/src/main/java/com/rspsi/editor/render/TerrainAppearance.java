package com.rspsi.editor.render;

/**
 * Final semantic terrain appearance inputs before frontend color conversion.
 * HSL values use the packed OSRS 6/3/7-bit representation.
 */
public record TerrainAppearance(
        int underlayHsl,
        int overlayHsl,
        int overlaySecondaryHsl,
        int textureId,
        int textureAverageHsl,
        int shape,
        int rotation,
        boolean overlayHidden
) {
    public TerrainAppearance {
        if (underlayHsl < 0 || overlayHsl < -1 || overlaySecondaryHsl < -1
                || textureId < -1 || textureAverageHsl < -1
                || shape < 0 || rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Invalid terrain appearance inputs");
        }
    }

    public static TerrainAppearance empty(int shape, int rotation) {
        return new TerrainAppearance(0, -1, -1, -1, -1, shape, rotation, false);
    }
}
