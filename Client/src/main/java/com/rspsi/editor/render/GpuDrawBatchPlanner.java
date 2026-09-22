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
        int cursor = 0;
        while (cursor < orderedIndices.size()) {
            int firstIndex = checkedIndex(commands, orderedIndices.get(cursor));
            GpuDrawCommand first = commands.get(firstIndex);
            if (first.pass() != pass) {
                throw new IllegalArgumentException("Ordered command does not belong to " + pass
                        + " pass: " + firstIndex);
            }

            long zoneKey = zoneKeyForCommand.applyAsLong(firstIndex);
            List<Integer> batchCommands = new ArrayList<>();
            batchCommands.add(firstIndex);

            int end = cursor + 1;
            while (end < orderedIndices.size()) {
                int candidateIndex = checkedIndex(commands, orderedIndices.get(end));
                GpuDrawCommand candidate = commands.get(candidateIndex);
                if (candidate.pass() != pass
                        || zoneKeyForCommand.applyAsLong(candidateIndex) != zoneKey
                        || !sameDrawState(first, candidate)) {
                    break;
                }
                batchCommands.add(candidateIndex);
                end++;
            }

            batches.add(new Batch(zoneKey, batchCommands));
            cursor = end;
        }
        return List.copyOf(batches);
    }

    private static int checkedIndex(List<GpuDrawCommand> commands, int index) {
        if (index < 0 || index >= commands.size()) {
            throw new IllegalArgumentException("Draw command index is outside the command list: " + index);
        }
        return index;
    }

    private static boolean sameDrawState(GpuDrawCommand first, GpuDrawCommand candidate) {
        return first.textureId() == candidate.textureId()
                && first.layer() == candidate.layer()
                && first.depthBias() == candidate.depthBias()
                && first.renderMode() == candidate.renderMode();
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
