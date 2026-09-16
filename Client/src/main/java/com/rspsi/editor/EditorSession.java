package com.rspsi.editor;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldModel;

import java.util.Objects;

/** Owns editor state without requiring JavaFX or a renderer. */
public final class EditorSession {
    private final WorldDocument world;
    private final SelectionModel selection = new SelectionModel();
    private final CommandHistory history = new CommandHistory();
    private int savedHistoryPosition;

    public EditorSession(WorldDocument world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public WorldDocument world() {
        return world;
    }

    /** @deprecated use {@link #world()}. */
    @Deprecated
    public WorldModel worldModel() {
        if (!(world instanceof WorldModel legacy)) {
            throw new IllegalStateException("Session document is not a legacy WorldModel");
        }
        return legacy;
    }

    public SelectionModel selection() {
        return selection;
    }

    public CommandHistory history() {
        return history;
    }

    public void execute(EditorCommand command) {
        history.execute(Objects.requireNonNull(command, "command"), this);
    }

    public boolean undo() {
        return history.undo(this);
    }

    public boolean redo() {
        return history.redo(this);
    }

    public void markSaved() {
        savedHistoryPosition = history.position();
    }

    public boolean isDirty() {
        return history.position() != savedHistoryPosition;
    }
}
