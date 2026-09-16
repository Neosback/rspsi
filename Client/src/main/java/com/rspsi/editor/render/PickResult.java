package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

/** Neutral result of viewport picking. Object picking is added later. */
public record PickResult(TileCoordinate tile, int plane) {
    public PickResult {
        if (tile == null || plane < 0) {
            throw new IllegalArgumentException("Pick result requires a valid tile and plane");
        }
    }
}
