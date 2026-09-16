package com.rspsi.editor.model;

import java.util.Objects;

/** UI-neutral tile inspection payload for status bars, panels, or overlays. */
public record TileInspectorSnapshot(
        WorldTileAddress address,
        TileSnapshot tile,
        boolean bridge,
        boolean roofRelated
) {
    public TileInspectorSnapshot {
        address = Objects.requireNonNull(address, "address");
        tile = Objects.requireNonNull(tile, "tile");
    }

    public int rawFlags() {
        return tile.flags();
    }
}
