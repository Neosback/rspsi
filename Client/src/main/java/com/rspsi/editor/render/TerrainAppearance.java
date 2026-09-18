package com.rspsi.editor.render;

/**
 * Final semantic terrain appearance inputs before frontend color conversion.
 * HSL values use the packed OSRS 6/3/7-bit representation.
 */
public record TerrainAppearance(
        int underlayHsl,
        int underlayHslSouthWest,
        int underlayHslSouthEast,
        int underlayHslNorthEast,
        int underlayHslNorthWest,
        int overlayHsl,
        int overlaySecondaryHsl,
        int textureId,
        int textureAverageHsl,
        int shape,
        int rotation,
        boolean overlayHidden,
        int overlayMinimapHsl
) {
    /** Compatibility constructor before underlay corner colors were exposed. */
    public TerrainAppearance(int underlayHsl, int overlayHsl, int overlaySecondaryHsl,
                             int textureId, int textureAverageHsl, int shape, int rotation,
                             boolean overlayHidden) {
        this(underlayHsl, underlayHsl, underlayHsl, underlayHsl, underlayHsl,
                overlayHsl, overlaySecondaryHsl, textureId, textureAverageHsl,
                shape, rotation, overlayHidden, overlayHsl);
    }

    /** Compatibility constructor before the minimap overlay HSL was explicit. */
    public TerrainAppearance(int underlayHsl, int underlayHslSouthWest,
                             int underlayHslSouthEast, int underlayHslNorthEast,
                             int underlayHslNorthWest, int overlayHsl,
                             int overlaySecondaryHsl, int textureId, int textureAverageHsl,
                             int shape, int rotation, boolean overlayHidden) {
        this(underlayHsl, underlayHslSouthWest, underlayHslSouthEast,
                underlayHslNorthEast, underlayHslNorthWest, overlayHsl,
                overlaySecondaryHsl, textureId, textureAverageHsl, shape, rotation,
                overlayHidden, overlayHsl);
    }

    public TerrainAppearance {
        if (underlayHsl < -1 || overlayHsl < -2 || overlaySecondaryHsl < -1
                || textureId < -1 || textureAverageHsl < -1
                || underlayHslSouthWest < -1 || underlayHslSouthEast < -1
                || underlayHslNorthEast < -1 || underlayHslNorthWest < -1
                || overlayMinimapHsl < -2
                || shape < 0 || rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Invalid terrain appearance inputs");
        }
    }

    public static TerrainAppearance empty(int shape, int rotation) {
        return new TerrainAppearance(0, 0, 0, 0, 0, -1, -1, -1, -1,
                shape, rotation, false, -1);
    }
}
