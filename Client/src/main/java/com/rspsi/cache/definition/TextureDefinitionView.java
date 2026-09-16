package com.rspsi.cache.definition;

/** Stable editor-facing subset of an OSRS texture definition. */
public record TextureDefinitionView(
        int id,
        boolean transparent,
        int fileId,
        int averageRgb,
        int animationDirection,
        int animationSpeed,
        boolean lowDetail
) {
    public TextureDefinitionView {
        if (id < 0 || fileId < -1) {
            throw new IllegalArgumentException("Invalid texture identity");
        }
    }
}
