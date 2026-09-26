package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/**
 * Unified selection containing one or more authored tile coordinates.
 *
 * <p>The Java record shell remains because it directly implements the sealed Java
 * {@link Selection} hierarchy. Input normalization lives in {@link SelectionSetSemantics}.</p>
 */
public record TileSetSelection(Set<TileCoordinate> coordinates) implements Selection {
    public TileSetSelection {
        coordinates = SelectionSetSemantics.normalizeTiles(coordinates);
    }
}
