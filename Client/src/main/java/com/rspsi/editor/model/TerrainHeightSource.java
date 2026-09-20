package com.rspsi.editor.model;

/**
 * Provenance for terrain heights.
 *
 * <p>{@code cacheEncoded} means the source can be replayed exactly through the
 * cache codec. {@code authored} identifies a height created by an editor
 * operation whose cache encoding must be derived when the document is saved.
 * Unknown is intentionally distinct from authored.</p>
 */
public record TerrainHeightSource(
        boolean generated,
        int explicitValue,
        boolean cacheEncoded,
        boolean authored) {

    public TerrainHeightSource {
        if (explicitValue < 0 || explicitValue > 255) {
            throw new IllegalArgumentException("Terrain height value must be between 0 and 255");
        }
        if (generated && authored) {
            throw new IllegalArgumentException("A generated cache height cannot also be editor-authored");
        }
    }

    /** Backward-compatible constructor for cache-decoder call sites. */
    public TerrainHeightSource(boolean generated, int explicitValue, boolean cacheEncoded) {
        this(generated, explicitValue, cacheEncoded, false);
    }

    public static TerrainHeightSource generatedSource() {
        return new TerrainHeightSource(true, 0, true, false);
    }

    public static TerrainHeightSource explicitSource(int value) {
        return new TerrainHeightSource(false, value == 1 ? 0 : value, true, false);
    }

    public static TerrainHeightSource authoredSource() {
        return new TerrainHeightSource(false, 0, false, true);
    }

    public static TerrainHeightSource unknown() {
        return new TerrainHeightSource(false, 0, false, false);
    }

    public boolean known() {
        return generated || cacheEncoded || authored;
    }
}
