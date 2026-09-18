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
        int renderType
) {
    public ModelTriangle {
        if (a < 0 || b < 0 || c < 0 || textureId < -1
                || alpha < 0 || alpha > 255 || priority < 0 || priority > 255
                || renderType < 0) {
            throw new IllegalArgumentException("Invalid model face attributes");
        }
    }
}
