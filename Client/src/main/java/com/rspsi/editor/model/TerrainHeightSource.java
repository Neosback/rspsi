package com.rspsi.editor.model;

/** Provenance needed when a terrain tile is replayed into an instance scene. */
public record TerrainHeightSource(boolean generated, int explicitValue, boolean cacheEncoded) {
    public TerrainHeightSource {
        if (explicitValue < 0 || explicitValue > 255) {
            throw new IllegalArgumentException("Terrain height value must be between 0 and 255");
        }
    }

    public static TerrainHeightSource generatedSource() {
        return new TerrainHeightSource(true, 0, true);
    }

    public static TerrainHeightSource explicitSource(int value) {
        return new TerrainHeightSource(false, value == 1 ? 0 : value, true);
    }

    public static TerrainHeightSource unknown() {
        return new TerrainHeightSource(false, 0, false);
    }
}
