package com.rspsi.editor;

/** Observes session state changes such as save-marker, edit, undo, or redo. */
@FunctionalInterface
public interface SessionStateListener {
    void changed(EditorSession session);
}
