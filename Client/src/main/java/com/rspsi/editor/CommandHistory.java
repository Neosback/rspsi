package com.rspsi.editor;

import java.util.ArrayList;
import java.util.List;

/** Non-UI undo/redo history. The cursor is also used for dirty-state tracking. */
public final class CommandHistory {
    private final List<EditCommand> commands = new ArrayList<>();
    private int cursor;

    public void execute(EditCommand command, EditorSession session) {
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

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < commands.size();
    }
}
