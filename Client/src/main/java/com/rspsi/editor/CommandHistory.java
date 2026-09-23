package com.rspsi.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Non-UI undo/redo history. The cursor is also used for dirty-state tracking. */
public final class CommandHistory {
    private final List<EditorCommand> commands = new ArrayList<>();
    // One token per history position, including position zero. A session-save
    // command creates a new token; an external transaction command carries the
    // current token forward. This keeps save-dirty checks O(1) and still
    // distinguishes replacement branches that happen to share a cursor index.
    private final List<Long> sessionSaveStateTokens = new ArrayList<>(List.of(0L));
    private long nextSessionSaveStateToken;
    private int cursor;

    public void execute(EditorCommand command, EditorSession session) {
        command.apply(session);
        while (commands.size() > cursor) {
            commands.remove(commands.size() - 1);
            sessionSaveStateTokens.remove(sessionSaveStateTokens.size() - 1);
        }
        commands.add(command);
        long currentToken = sessionSaveStateTokens.get(cursor);
        long nextToken = command.savedBySessionSave()
                ? ++nextSessionSaveStateToken
                : currentToken;
        sessionSaveStateTokens.add(nextToken);
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

    /**
     * Moves the history cursor to an exact position and returns all tiles
     * touched while replaying the transition. A failed replay is rolled back
     * to the original cursor whenever the commands' inverse operations allow
     * it, so callers never record a partial jump.
     */
    public Set<com.rspsi.editor.model.TileCoordinate> moveTo(int target, EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (target < 0 || target > commands.size()) {
            throw new IllegalArgumentException("History position outside [0, " + commands.size() + "]: " + target);
        }
        int origin = cursor;
        if (target == origin) return Set.of();
        Set<com.rspsi.editor.model.TileCoordinate> changed = new LinkedHashSet<>();
        if (target < origin) {
            try {
                while (cursor > target) {
                    EditorCommand command = commands.get(cursor - 1);
                    command.undo(session);
                    cursor--;
                    changed.addAll(command.changedTiles());
                }
            } catch (RuntimeException failure) {
                restoreUndone(origin, session, failure);
                throw failure;
            }
        } else {
            try {
                while (cursor < target) {
                    EditorCommand command = commands.get(cursor);
                    command.apply(session);
                    cursor++;
                    changed.addAll(command.changedTiles());
                }
            } catch (RuntimeException failure) {
                rollbackApplied(origin, session, failure);
                throw failure;
            }
        }
        return Set.copyOf(changed);
    }

    private void restoreUndone(int origin, EditorSession session, RuntimeException failure) {
        while (cursor < origin) {
            try {
                commands.get(cursor).apply(session);
                cursor++;
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                return;
            }
        }
    }

    private void rollbackApplied(int origin, EditorSession session, RuntimeException failure) {
        while (cursor > origin) {
            try {
                commands.get(cursor - 1).undo(session);
                cursor--;
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                return;
            }
        }
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

    /**
     * Identity token for the currently applied state that is persisted by the
     * normal {@link EditorSession} save handler.
     */
    public long sessionSaveStateToken() {
        return sessionSaveStateTokens.get(cursor);
    }

    EditorCommand previousCommand() {
        return commands.get(cursor - 1);
    }

    EditorCommand nextCommand() {
        return commands.get(cursor);
    }
}
