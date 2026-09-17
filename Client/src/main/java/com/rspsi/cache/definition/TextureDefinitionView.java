package com.rspsi.cache.definition;

/** Stable editor-facing subset of an OSRS texture definition. */
public record TextureDefinitionView(
        int id,
        boolean transparent,
        int fileId,
        int averageRgb,
        /** OSRS texture-definition average HSL; -1 when unavailable. */
        int averageHsl,
        int animationDirection,
        int animationSpeed,
        boolean lowDetail
) {
    /** Source-compatible constructor for providers that expose only RGB metadata. */
    public TextureDefinitionView(int id, boolean transparent, int fileId, int averageRgb,
                                 int animationDirection, int animationSpeed, boolean lowDetail) {
        this(id, transparent, fileId, averageRgb, -1, animationDirection, animationSpeed, lowDetail);
    }

    public TextureDefinitionView {
        if (id < 0 || fileId < -1) {
            throw new IllegalArgumentException("Invalid texture identity");
        }
    }
}
