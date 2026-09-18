package com.rspsi.editor.render;

/** Model texture-triangle mapping retained for faithful face UV reconstruction. */
public record TextureTriangle(int a, int b, int c) {
    public TextureTriangle {
        if (a < 0 || b < 0 || c < 0) {
            throw new IllegalArgumentException("Texture triangle indices cannot be negative");
        }
    }
}
