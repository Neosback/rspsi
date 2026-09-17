package com.rspsi.cache.definition;

import java.util.Objects;

/**
 * Neutral ARGB map-scene sprite used by minimap and world-map composition.
 * Pixel value zero is transparent; all other values are copied as ARGB.
 */
public record MapSceneSpriteView(
        int id,
        int width,
        int height,
        int offsetX,
        int offsetY,
        int[] argb
) {
    public MapSceneSpriteView {
        if (id < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid map-scene sprite dimensions");
        }
        Objects.requireNonNull(argb, "argb");
        if (argb.length != width * height) {
            throw new IllegalArgumentException("Map-scene pixel count does not match dimensions");
        }
        argb = argb.clone();
    }

    @Override
    public int[] argb() {
        return argb.clone();
    }
}
