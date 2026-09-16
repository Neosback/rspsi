package com.rspsi.editor.model;

import java.util.Objects;

/** A terrain snapshot at its source coordinate inside a world fragment. */
public record TerrainTilePatch(int plane, int x, int y, TileSnapshot snapshot) {
    public TerrainTilePatch {
        if (plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("Fragment tile coordinates cannot be negative");
        }
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }
}
