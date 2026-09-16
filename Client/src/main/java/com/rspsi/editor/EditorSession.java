package com.rspsi.editor;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldModel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns editor state without requiring JavaFX or a renderer. */
public final class EditorSession {
    private final WorldDocument world;
    private final SelectionModel selection = new SelectionModel();
    private final CommandHistory history = new CommandHistory();
    private final List<SessionChangeListener> changeListeners = new CopyOnWriteArrayList<>();
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
        EditorCommand checked = Objects.requireNonNull(command, "command");
        history.execute(checked, this);
        notifyChanged(checked);
    }

    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        EditorCommand command = history.previousCommand();
        boolean changed = history.undo(this);
        if (changed) {
            notifyChanged(command);
        }
        return changed;
    }

    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        EditorCommand command = history.nextCommand();
        boolean changed = history.redo(this);
        if (changed) {
            notifyChanged(command);
        }
        return changed;
    }

    public void markSaved() {
        savedHistoryPosition = history.position();
    }

    public boolean isDirty() {
        return history.position() != savedHistoryPosition;
    }

    public void addChangeListener(SessionChangeListener listener) {
        changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(SessionChangeListener listener) {
        changeListeners.remove(listener);
    }

    private void notifyChanged(EditorCommand command) {
        if (command.changedTiles().isEmpty()) {
            return;
        }
        var changedTiles = Set.copyOf(command.changedTiles());
        for (SessionChangeListener listener : changeListeners) {
            listener.changed(changedTiles);
        }
    }
}
