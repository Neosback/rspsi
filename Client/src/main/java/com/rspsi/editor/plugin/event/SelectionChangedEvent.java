package com.rspsi.editor.plugin.event;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/**
 * JVM record shell for selection-change events.
 *
 * <p>Defensive set normalization lives in {@link SelectionChangedEventSemantics} so the event
 * keeps its historical Java record identity and constructor descriptor.</p>
 */
public record SelectionChangedEvent(Set<TileCoordinate> selectedTiles) {
    public SelectionChangedEvent {
        selectedTiles = SelectionChangedEventSemantics.normalize(selectedTiles);
    }
}
