package com.rspsi.editor.tool;

import com.rspsi.editor.model.TileSnapshot;

/** Backend- and frontend-neutral bilinear height sampling for one tile. */
public final class TerrainHeightSampler {
    private TerrainHeightSampler() {
    }

    /** Samples the tile at normalized local coordinates in the inclusive 0..1 range. */
    public static int sample(TileSnapshot tile, double localX, double localY) {
        if (tile == null) throw new NullPointerException("tile");
        if (localX < 0 || localX > 1 || localY < 0 || localY > 1) {
            throw new IllegalArgumentException("Local tile coordinates must be between zero and one");
        }
        double south = tile.southWestHeight()
                + (tile.southEastHeight() - tile.southWestHeight()) * localX;
        double north = tile.northWestHeight()
                + (tile.northEastHeight() - tile.northWestHeight()) * localX;
        return (int) Math.round(south + (north - south) * localY);
    }
}
