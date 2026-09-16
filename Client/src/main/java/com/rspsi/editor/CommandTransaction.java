package com.rspsi.editor;

import java.util.List;
import java.util.Objects;

/** Package-private atomic application helper shared by grouped commands. */
final class CommandTransaction {
    private CommandTransaction() {
    }

    static void apply(List<? extends EditorCommand> commands, EditorSession session) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(session, "session");
        int applied = 0;
        try {
            for (EditorCommand command : commands) {
                command.apply(session);
                applied++;
            }
        } catch (RuntimeException failure) {
            for (int index = applied - 1; index >= 0; index--) {
                commands.get(index).undo(session);
            }
            throw failure;
        }
    }
}
