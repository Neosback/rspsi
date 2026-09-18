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

    public ModelTriangle withColors(int newColorA, int newColorB, int newColorC) {
        return new ModelTriangle(a, b, c, newColorA, newColorB, newColorC,
                textureId, alpha, priority, renderType, uA, vA, uB, vB, uC, vC,
                baseColor, depthBias);
    }
}
