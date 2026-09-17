package com.rspsi.editor.render;

/** Frontend-only presentation adjustment; never part of authored scene data. */
public record LightingExposure(double value) {
    public LightingExposure {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Exposure must be finite and positive");
        }
    }

    public static LightingExposure neutral() {
        return new LightingExposure(1.0);
    }
}
