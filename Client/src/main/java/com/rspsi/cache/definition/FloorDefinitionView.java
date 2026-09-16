package com.rspsi.cache.definition;

/** Stable, editor-facing floor definition. */
public record FloorDefinitionView(
        int id,
        int texture,
        int rgb,
        int hue,
        int saturation,
        int luminance,
        int weightedHue,
        int chroma
) {
}
