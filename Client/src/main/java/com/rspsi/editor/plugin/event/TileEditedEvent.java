package com.rspsi.editor.plugin.event;

import com.rspsi.editor.model.TileCoordinate;
import java.util.Set;

public record TileEditedEvent(Set<TileCoordinate> tiles, String description) {
    public TileEditedEvent {
        tiles = Set.copyOf(tiles == null ? Set.of() : tiles);
        description = description == null ? "" : description;
    }
}
