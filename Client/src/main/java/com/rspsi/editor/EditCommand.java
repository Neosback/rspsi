package com.rspsi.editor;

/** Atomic, undoable editor operation. */
public interface EditCommand {
    void apply(EditorSession session);

    void undo(EditorSession session);

    String description();
}
