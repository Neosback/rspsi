package com.rspsi.editor.plugin.event;

import com.rspsi.editor.model.TileCoordinate;
import java.util.Set;

public record SelectionChangedEvent(Set<TileCoordinate> selectedTiles) {
    public SelectionChangedEvent {
        selectedTiles = Set.copyOf(selectedTiles == null ? Set.of() : selectedTiles);
    }
}
