package com.rspsi.editor.render;

/**
 * RuneScape terrain-space normal using the client's 256-scale convention.
 *
 * <p>The vector is already normalized; magnitude is retained to match the
 * renderer-neutral model-normal contract.</p>
 */
public record TerrainNormal(int x, int y, int z, int magnitude) {
    public static final TerrainNormal FLAT = new TerrainNormal(0, 256, 0, 1);

    public TerrainNormal {
        if (magnitude < 0) {
            throw new IllegalArgumentException("Terrain normal magnitude cannot be negative");
        }
    }
}
