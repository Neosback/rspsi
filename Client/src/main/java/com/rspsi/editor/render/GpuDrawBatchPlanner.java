package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntToLongFunction;

/**
 * Backend-neutral planner for grouping already-ordered GPU draw commands into
 * native draw batches.
 *
 * <p>A batch may span several commands only when they resolve to the same GPU
 * zone allocation and require identical render state. Priority is deliberately
 * not part of the state key: callers provide commands in final priority order,
 * and a multi-draw preserves that command order while avoiding redundant state
 * changes and draw calls.</p>
 */
public final class GpuDrawBatchPlanner {
    private GpuDrawBatchPlanner() {
    }

    public static List<Batch> plan(List<GpuDrawCommand> commands,
                                   List<Integer> orderedIndices,
                                   GpuDrawCommand.SubmissionPass pass,
                                   IntToLongFunction zoneKeyForCommand) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(orderedIndices, "orderedIndices");
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(zoneKeyForCommand, "zoneKeyForCommand");

        if (orderedIndices.isEmpty()) return List.of();

        List<Batch> batches = new ArrayList<>();
        BatchCursor cursor = cursor(commands, orderedIndices, pass, zoneKeyForCommand);
        while (cursor.next()) {
            List<Integer> batchCommands = new ArrayList<>(cursor.commandCount());
            for (int offset = 0; offset < cursor.commandCount(); offset++) {
                batchCommands.add(cursor.commandIndexAt(offset));
            }
            batches.add(new Batch(cursor.zoneKey(), batchCommands));
        }
        return List.copyOf(batches);
    }

    /**
     * Allocation-light cursor over native draw batches.
     *
     * <p>The compatibility {@link #plan} API materializes immutable batch
     * records. Native render loops should prefer this cursor: it groups the
     * caller's already-ordered command indices in place and exposes each
     * contiguous batch without copying command indices into nested lists.</p>
     */
    public static BatchCursor cursor(List<GpuDrawCommand> commands,
                                     List<Integer> orderedIndices,
                                     GpuDrawCommand.SubmissionPass pass,
                                     IntToLongFunction zoneKeyForCommand) {
        return new BatchCursor(commands, orderedIndices, pass, zoneKeyForCommand);
    }

    public static final class BatchCursor {
        private final List<GpuDrawCommand> commands;
        private final List<Integer> orderedIndices;
        private final GpuDrawCommand.SubmissionPass pass;
        private final IntToLongFunction zoneKeyForCommand;
        private int cursor;
        private int start;
        private int end;
        private long zoneKey;
        private boolean positioned;

        private BatchCursor(List<GpuDrawCommand> commands,
                            List<Integer> orderedIndices,
                            GpuDrawCommand.SubmissionPass pass,
                            IntToLongFunction zoneKeyForCommand) {
            this.commands = Objects.requireNonNull(commands, "commands");
            this.orderedIndices = Objects.requireNonNull(orderedIndices, "orderedIndices");
            this.pass = Objects.requireNonNull(pass, "pass");
            this.zoneKeyForCommand = Objects.requireNonNull(
                    zoneKeyForCommand, "zoneKeyForCommand");
        }

        public boolean next() {
            if (cursor >= orderedIndices.size()) {
                positioned = false;
                return false;
            }

            start = cursor;
            int firstIndex = checkedIndex(commands, orderedIndices.get(start));
            GpuDrawCommand first = commands.get(firstIndex);
            if (first.pass() != pass) {
                throw new IllegalArgumentException(
                        "Ordered command does not belong to " + pass
                                + " pass: " + firstIndex);
            }

            zoneKey = zoneKeyForCommand.applyAsLong(firstIndex);
            end = start + 1;
            while (end < orderedIndices.size()) {
                int candidateIndex =
                        checkedIndex(commands, orderedIndices.get(end));
                GpuDrawCommand candidate = commands.get(candidateIndex);
                if (candidate.pass() != pass
                        || zoneKeyForCommand.applyAsLong(candidateIndex) != zoneKey
                        || !sameDrawState(first, candidate)) {
                    break;
                }
                end++;
            }

            cursor = end;
            positioned = true;
            return true;
        }

        public long zoneKey() {
            ensurePositioned();
            return zoneKey;
        }

        public int firstCommandIndex() {
            return commandIndexAt(0);
        }

        public int commandCount() {
            ensurePositioned();
            return end - start;
        }

        public int commandIndexAt(int offset) {
            ensurePositioned();
            if (offset < 0 || start + offset >= end) {
                throw new IndexOutOfBoundsException(
                        "Batch command offset outside current range: " + offset);
            }
            return checkedIndex(commands, orderedIndices.get(start + offset));
        }

        private void ensurePositioned() {
            if (!positioned) {
                throw new IllegalStateException("Batch cursor is not positioned");
            }
        }
    }

    private static int checkedIndex(List<GpuDrawCommand> commands, int index) {
        if (index < 0 || index >= commands.size()) {
            throw new IllegalArgumentException("Draw command index is outside the command list: " + index);
        }
        return index;
    }

    private static boolean sameDrawState(GpuDrawCommand first, GpuDrawCommand candidate) {
        return RenderOrderKey.sameNativeState(first, candidate);
    }

    /** One native draw call, potentially containing several ordered command ranges. */
    public record Batch(long zoneKey, List<Integer> commandIndices) {
        public Batch {
            commandIndices = List.copyOf(Objects.requireNonNull(commandIndices, "commandIndices"));
            if (commandIndices.isEmpty()) {
                throw new IllegalArgumentException("A draw batch must contain at least one command");
            }
        }

        public int firstCommandIndex() {
            return commandIndices.get(0);
        }

        public int commandCount() {
            return commandIndices.size();
        }
    }
}
