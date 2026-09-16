package com.rspsi.editor;

import java.util.List;

/** Groups several edits into one user-visible history entry. */
public final class CompositeEditCommand implements EditCommand {
    private final String description;
    private final List<EditorCommand> commands;

    public CompositeEditCommand(String description, List<? extends EditorCommand> commands) {
        this.description = description;
        this.commands = List.copyOf(commands);
    }

    @Override
    public void apply(EditorSession session) {
        for (EditorCommand command : commands) {
            command.apply(session);
        }
    }

    @Override
    public void undo(EditorSession session) {
        for (int index = commands.size() - 1; index >= 0; index--) {
            commands.get(index).undo(session);
        }
    }

    @Override
    public String description() {
        return description;
    }
}
