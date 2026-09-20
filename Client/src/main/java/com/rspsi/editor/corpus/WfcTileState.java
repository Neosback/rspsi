package com.rspsi.editor.corpus;

import com.rspsi.editor.model.TileSnapshot;

/**
 * Cache-authentic WFC socket state. Height fields are corner deltas relative
 * to SW, so generated adjacency can preserve slope continuity without locking
 * a biome to one absolute elevation.
 */
public record WfcTileState(
        int underlayId,
        int overlayId,
        int shape,
        int rotation,
        int flags,
        int southEastDelta,
        int northEastDelta,
        int northWestDelta) {

    public static WfcTileState from(TileSnapshot tile) {
        int base = tile.southWestHeight();
        return new WfcTileState(tile.underlayId(), tile.overlayId(),
                tile.overlayShape(), tile.overlayRotation(), tile.flags(),
                tile.southEastHeight() - base,
                tile.northEastHeight() - base,
                tile.northWestHeight() - base);
    }
}
