package com.rspsi.editor.minimap;

import java.util.Objects;

/** Immutable frontend-neutral ARGB minimap raster for one document plane. */
public record MinimapImage(int plane, int width, int height, int[] argb) {
    public MinimapImage {
        if (plane < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid minimap dimensions");
        }
        Objects.requireNonNull(argb, "argb");
        if (argb.length != width * height) {
            throw new IllegalArgumentException("Minimap pixel count does not match dimensions");
        }
        argb = argb.clone();
    }

    @Override
    public int[] argb() {
        return argb.clone();
    }

    /** Returns one ARGB pixel using document x/y coordinates. */
    public int pixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Pixel outside minimap: " + x + "," + y);
        }
        return argb[y * width + x];
    }
}
