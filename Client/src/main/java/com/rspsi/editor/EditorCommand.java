package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/** Canonical atomic, undoable editor operation. */
public interface EditorCommand {
    void apply(EditorSession session);

    void undo(EditorSession session);

    String description();

    /** Tiles whose derived scene data may need refresh after this command. */
    default Set<TileCoordinate> changedTiles() {
        return Set.of();
    }
}
