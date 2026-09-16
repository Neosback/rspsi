package com.rspsi.editor.model;

/** Immutable world coordinate for a tile. */
public record TileCoordinate(int plane, int x, int y) {
    public TileCoordinate {
        if (plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("Tile coordinates cannot be negative");
        }
    }
}
