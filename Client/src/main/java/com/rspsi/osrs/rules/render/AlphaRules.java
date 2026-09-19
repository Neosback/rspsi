package com.rspsi.osrs.rules.render;

/**
 * Formal OSRS face alpha sentinels and transparency clamping rules.
 */
public final class AlphaRules {
    private AlphaRules() {}

    /**
     * Resolves the face render type from raw alpha value sentinels.
     * The client encodes face render types 2 and 3 through alpha sentinels -1 and -2.
     */
    public static int resolveRenderType(int rawAlpha, int declaredRenderType) {
        if (rawAlpha == -1) return 2;
        if (rawAlpha == -2) return 3;
        return declaredRenderType;
    }

    /** Clamps standard alpha transparency into 0..255 range. */
    public static int clampAlpha(int rawAlpha) {
        return Math.max(0, Math.min(255, rawAlpha));
    }
}
