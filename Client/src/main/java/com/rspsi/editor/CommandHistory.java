package com.rspsi.editor;

import java.util.ArrayList;
import java.util.List;

/** Non-UI undo/redo history. The cursor is also used for dirty-state tracking. */
public final class CommandHistory {
    private final List<EditorCommand> commands = new ArrayList<>();
    private int cursor;

    public void execute(EditorCommand command, EditorSession session) {
        command.apply(session);
        while (commands.size() > cursor) {
            commands.remove(commands.size() - 1);
        }
        commands.add(command);
        cursor++;
    }

    public boolean undo(EditorSession session) {
        if (cursor == 0) {
            return false;
        }
        commands.get(cursor - 1).undo(session);
        cursor--;
        return true;
    }

    public boolean redo(EditorSession session) {
        if (cursor == commands.size()) {
            return false;
        }
        commands.get(cursor).apply(session);
        cursor++;
        return true;
    }

    public int position() {
        return cursor;
    }

    public int size() {
        return commands.size();
    }

    /** Returns the complete immutable history, including commands after the cursor. */
    public List<EditorCommand> commands() {
        return List.copyOf(commands);
    }

    /** Number of commands currently applied; commands after this are redoable. */
    public int cursor() {
        return cursor;
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < commands.size();
    }

    EditorCommand previousCommand() {
        return commands.get(cursor - 1);
    }

    EditorCommand nextCommand() {
        return commands.get(cursor);
    }
}
