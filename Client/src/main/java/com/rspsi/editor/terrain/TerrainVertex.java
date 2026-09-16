package com.rspsi.editor.terrain;

/** A terrain mesh vertex in one tile's local 128x128 coordinate system. */
public record TerrainVertex(int x, int y, int height) {
    public TerrainVertex {
        if (x < 0 || x > 128 || y < 0 || y > 128) {
            throw new IllegalArgumentException("Terrain vertex must be inside a tile");
        }
    }
}
