package com.rspsi.editor.render;

/** Model texture-triangle mapping and animation metadata for GPU sampling. */
public record TextureTriangle(
        int a,
        int b,
        int c,
        int renderType,
        int scaleX,
        int scaleY,
        int scaleZ,
        int rotation,
        int direction,
        int speed,
        int translationU,
        int translationV
) {
    /** Compatibility constructor for packets created before texture metadata was retained. */
    public TextureTriangle(int a, int b, int c) {
        this(a, b, c, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public TextureTriangle {
        if (a < 0 || b < 0 || c < 0) {
            throw new IllegalArgumentException("Texture triangle indices cannot be negative");
        }
        // Metadata is retained as raw revision data. Some cache revisions use
        // sentinel or signed values, so validation is limited to topology.
    }
}
