package com.rspsi.editor.render;

/** Model face attributes retained before backend packing. */
public record ModelTriangle(
        int a,
        int b,
        int c,
        int colorA,
        int colorB,
        int colorC,
        int textureId,
        int alpha,
        int priority,
        int renderType,
        float uA,
        float vA,
        float uB,
        float vB,
        float uC,
        float vC,
        int baseColor,
        int depthBias
) {
    /** Model alpha is transparency: 0 is opaque and 255 is fully invisible. */
    /** Compatibility constructor for packets created before per-face UVs. */
    public ModelTriangle(int a, int b, int c, int colorA, int colorB, int colorC,
                         int textureId, int alpha, int priority, int renderType) {
        this(a, b, c, colorA, colorB, colorC, textureId, alpha, priority, renderType,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, colorA, 0);
    }

    /** Compatibility constructor before unlit source HSL was retained. */
    public ModelTriangle(int a, int b, int c, int colorA, int colorB, int colorC,
                         int textureId, int alpha, int priority, int renderType,
                         float uA, float vA, float uB, float vB, float uC, float vC) {
        this(a, b, c, colorA, colorB, colorC, textureId, alpha, priority, renderType,
                uA, vA, uB, vB, uC, vC, colorA, 0);
    }

    public ModelTriangle {
        if (a < 0 || b < 0 || c < 0 || textureId < -1
                || alpha < 0 || alpha > 255 || priority < 0 || priority > 255
                || renderType < 0 || depthBias < 0 || depthBias > 255) {
            throw new IllegalArgumentException("Invalid model face attributes");
        }
        if (!Float.isFinite(uA) || !Float.isFinite(vA) || !Float.isFinite(uB)
                || !Float.isFinite(vB) || !Float.isFinite(uC) || !Float.isFinite(vC)) {
            throw new IllegalArgumentException("Model face UV coordinates must be finite");
        }
    }

    /** Post-recolor unlit Jagex HSL corresponding to RuneLite Model#getUnlitFaceColors. */
    public int unlitColor() {
        return baseColor;
    }

    /** True when colorC carries the client's flat-shading sentinel. */
    public boolean flatShaded() {
        return colorC == ModelFaceColorContract.FLAT_SENTINEL;
    }

    /** True when colorC carries the client's skipped-face sentinel. */
    public boolean skippedByColorContract() {
        return colorC == ModelFaceColorContract.SKIP_SENTINEL;
    }

    public ModelTriangle withColors(int newColorA, int newColorB, int newColorC) {
        return new ModelTriangle(a, b, c, newColorA, newColorB, newColorC,
                textureId, alpha, priority, renderType, uA, vA, uB, vB, uC, vC,
                baseColor, depthBias);
    }

    public ModelTriangle withAlpha(int newAlpha) {
        return new ModelTriangle(a, b, c, colorA, colorB, colorC,
                textureId, newAlpha, priority, renderType, uA, vA, uB, vB, uC, vC,
                baseColor, depthBias);
    }

    public ModelTriangle withRenderType(int newRenderType) {
        return new ModelTriangle(a, b, c, colorA, colorB, colorC,
                textureId, alpha, priority, newRenderType, uA, vA, uB, vB, uC, vC,
                baseColor, depthBias);
    }
}
