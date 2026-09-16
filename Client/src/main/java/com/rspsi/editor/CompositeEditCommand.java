package com.rspsi.editor;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @Override
    public Set<com.rspsi.editor.model.TileCoordinate> changedTiles() {
        Set<com.rspsi.editor.model.TileCoordinate> changed = new LinkedHashSet<>();
        for (EditorCommand command : commands) {
            changed.addAll(command.changedTiles());
        }
        return Set.copyOf(changed);
    }
}
