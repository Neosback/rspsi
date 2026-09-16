package com.rspsi.cache.definition;

/** Stable, editor-facing floor definition. */
public record FloorDefinitionView(
        int id,
        int texture,
        int rgb,
        int hue,
        int saturation,
        int luminance,
        /** Weighted hue numerator used by OSRS underlay blending. */
        int weightedHue,
        /** Chroma/hue multiplier used as the OSRS blend denominator. */
        int chroma
) {
}
