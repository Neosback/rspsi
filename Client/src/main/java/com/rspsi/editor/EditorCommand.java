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

    /**
     * Whether {@link EditorSession#markSaved()} persists this command's state.
     *
     * <p>Map/document commands use the default. Commands that edit isolated
     * external transactions, such as definition previews, return false so a
     * map save cannot incorrectly mark that separate state as durable.</p>
     */
    default boolean savedBySessionSave() {
        return true;
    }

    /**
     * Reports dirty state owned outside the normal session save handler.
     *
     * <p>This is only consulted for commands where
     * {@link #savedBySessionSave()} is false. Implementations should report
     * the current state of the shared external transaction rather than merely
     * returning true because a historical command exists.</p>
     */
    default boolean hasUnsavedExternalState() {
        return !savedBySessionSave();
    }
}
