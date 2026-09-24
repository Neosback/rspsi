package com.rspsi.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One user-visible history for change plans that span several canonical region
 * sessions.
 *
 * <p>The existing per-region histories remain the storage/dirty-state
 * mechanism. This coordinator verifies exact command identity before undo or
 * redo, so a local history divergence is detected before any region mutates.</p>
 */
public final class WorldRegionChangeHistory {
    private final List<Entry> entries = new ArrayList<>();
    private int cursor;

    public int position() {
        return cursor;
    }

    public int size() {
        return entries.size();
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < entries.size();
    }

    public String undoDescription() {
        return canUndo() ? entries.get(cursor - 1).description() : "";
    }

    public String redoDescription() {
        return canRedo() ? entries.get(cursor).description() : "";
    }

    void execute(String description, List<PendingRegionEdit> edits) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(edits, "edits");
        if (description.isBlank()) {
            throw new IllegalArgumentException("History description cannot be blank");
        }
        if (edits.isEmpty()) {
            throw new IllegalArgumentException("Cross-region history entry cannot be empty");
        }

        List<RegionStep> applied = new ArrayList<>(edits.size());
        try {
            for (PendingRegionEdit edit : edits) {
                EditorSession session = edit.session();
                int before = session.history().position();
                session.execute(edit.command());
                int after = session.history().position();
                if (after != before + 1
                        || session.history().previousCommand() != edit.command()) {
                    throw new IllegalStateException(
                            "Region history did not retain the committed command identity");
                }
                applied.add(new RegionStep(
                        edit.regionId(), session, edit.command(), before, after));
            }
        } catch (RuntimeException failure) {
            rollbackApplied(applied, failure);
            throw failure;
        }

        while (entries.size() > cursor) {
            entries.remove(entries.size() - 1);
        }
        entries.add(new Entry(description, List.copyOf(applied)));
        cursor++;
    }

    public boolean undo() {
        if (!canUndo()) return false;
        Entry entry = entries.get(cursor - 1);
        validateUndo(entry);

        List<RegionStep> undone = new ArrayList<>();
        try {
            List<RegionStep> steps = entry.steps();
            for (int index = steps.size() - 1; index >= 0; index--) {
                RegionStep step = steps.get(index);
                if (!step.session().undo()) {
                    throw new IllegalStateException(
                            "Region history refused cross-region undo for " + step.regionId());
                }
                undone.add(step);
            }
        } catch (RuntimeException failure) {
            restoreUndone(undone, failure);
            throw failure;
        }
        cursor--;
        return true;
    }

    public boolean redo() {
        if (!canRedo()) return false;
        Entry entry = entries.get(cursor);
        validateRedo(entry);

        List<RegionStep> redone = new ArrayList<>();
        try {
            for (RegionStep step : entry.steps()) {
                if (!step.session().redo()) {
                    throw new IllegalStateException(
                            "Region history refused cross-region redo for " + step.regionId());
                }
                redone.add(step);
            }
        } catch (RuntimeException failure) {
            rollbackRedone(redone, failure);
            throw failure;
        }
        cursor++;
        return true;
    }

    private static void validateUndo(Entry entry) {
        for (RegionStep step : entry.steps()) {
            CommandHistory history = step.session().history();
            if (history.position() != step.afterPosition()
                    || !history.canUndo()
                    || history.previousCommand() != step.command()) {
                throw new IllegalStateException(
                        "Region history diverged before undo: " + step.regionId());
            }
        }
    }

    private static void validateRedo(Entry entry) {
        for (RegionStep step : entry.steps()) {
            CommandHistory history = step.session().history();
            if (history.position() != step.beforePosition()
                    || !history.canRedo()
                    || history.nextCommand() != step.command()) {
                throw new IllegalStateException(
                        "Region history diverged before redo: " + step.regionId());
            }
        }
    }

    private static void rollbackApplied(List<RegionStep> applied, RuntimeException failure) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            RegionStep step = applied.get(index);
            try {
                CommandHistory history = step.session().history();
                if (history.position() == step.afterPosition()
                        && history.canUndo()
                        && history.previousCommand() == step.command()) {
                    step.session().undo();
                }
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void restoreUndone(List<RegionStep> undone, RuntimeException failure) {
        for (int index = undone.size() - 1; index >= 0; index--) {
            RegionStep step = undone.get(index);
            try {
                CommandHistory history = step.session().history();
                if (history.position() == step.beforePosition()
                        && history.canRedo()
                        && history.nextCommand() == step.command()) {
                    step.session().redo();
                }
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void rollbackRedone(List<RegionStep> redone, RuntimeException failure) {
        for (int index = redone.size() - 1; index >= 0; index--) {
            RegionStep step = redone.get(index);
            try {
                CommandHistory history = step.session().history();
                if (history.position() == step.afterPosition()
                        && history.canUndo()
                        && history.previousCommand() == step.command()) {
                    step.session().undo();
                }
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    record PendingRegionEdit(int regionId, EditorSession session, EditorCommand command) {
        PendingRegionEdit {
            session = Objects.requireNonNull(session, "session");
            command = Objects.requireNonNull(command, "command");
        }
    }

    private record RegionStep(
            int regionId,
            EditorSession session,
            EditorCommand command,
            int beforePosition,
            int afterPosition
    ) {
        private RegionStep {
            session = Objects.requireNonNull(session, "session");
            command = Objects.requireNonNull(command, "command");
            if (beforePosition < 0 || afterPosition != beforePosition + 1) {
                throw new IllegalArgumentException("Invalid region-history positions");
            }
        }
    }

    private record Entry(String description, List<RegionStep> steps) {
        private Entry {
            description = Objects.requireNonNull(description, "description");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        }
    }
}
