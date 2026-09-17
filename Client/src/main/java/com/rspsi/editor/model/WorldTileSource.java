package com.rspsi.editor.model;

import java.util.Objects;

/** A decoded tile plus the cache height opcode semantics that produced it. */
public record WorldTileSource(TileSnapshot snapshot, TerrainHeightSource heightSource) {
    public WorldTileSource {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        heightSource = Objects.requireNonNull(heightSource, "heightSource");
    }
}
