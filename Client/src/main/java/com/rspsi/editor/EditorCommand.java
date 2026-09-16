package com.rspsi.editor;

/** Canonical atomic, undoable editor operation. */
public interface EditorCommand {
    void apply(EditorSession session);

    void undo(EditorSession session);

    String description();
}
