package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

/** Neutral result of viewport picking across both 2D and 3D backends. */
public record PickResult(TileCoordinate tile, int plane, int objectId, float distance) {
    /** Compatibility constructor for tile-only legacy viewport picking. */
    public PickResult(TileCoordinate tile, int plane) {
        this(tile, plane, -1, Float.NaN);
    }

    public PickResult {
        if (tile == null || plane < 0 || plane != tile.plane() || objectId < -1
                || (!Float.isNaN(distance) && (!Float.isFinite(distance) || distance < 0.0f))) {
            throw new IllegalArgumentException("Pick result requires a valid tile and plane");
        }
    }

    public boolean objectHit() {
        return objectId >= 0;
    }
}
