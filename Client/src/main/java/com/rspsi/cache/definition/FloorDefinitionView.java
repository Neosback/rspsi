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
        int chroma,
        /** Optional secondary floor color; -1 means that no secondary color exists. */
        int secondaryRgb,
        int secondaryHue,
        int secondarySaturation,
        int secondaryLuminance
) {
    /** Source-compatible constructor for providers that expose only primary floor data. */
    public FloorDefinitionView(int id, int texture, int rgb, int hue, int saturation,
                               int luminance, int weightedHue, int chroma) {
        this(id, texture, rgb, hue, saturation, luminance, weightedHue, chroma,
                -1, 0, 0, 0);
    }
}
