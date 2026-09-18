package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Reproduces the legacy model face-priority interleave used by the OSRS
 * software renderer for alpha faces.
 *
 * <p>This is intentionally backend-neutral. The OpenGL renderer supplies the
 * camera-depth function, while the planner owns the unusual RuneScape rule:
 * priority groups 1/2, 3/4, 6/8, and the 10/11 streams are interleaved using
 * their average depths rather than being drawn as one simple sorted list.</p>
 */
public final class RsFaceOrderPlanner {
    private RsFaceOrderPlanner() {
    }

    public static List<GpuDrawCommand> orderAlpha(List<GpuDrawCommand> commands,
                                                   ToDoubleFunction<GpuDrawCommand> depth) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(depth, "depth");
        if (commands.size() < 2) return List.copyOf(commands);

        List<List<GpuDrawCommand>> groups = new ArrayList<>(12);
        for (int index = 0; index < 12; index++) groups.add(new ArrayList<>());
        for (GpuDrawCommand command : commands) {
            int priority = Math.max(0, Math.min(11, command.priority()));
            groups.get(priority).add(command);
        }
        Comparator<GpuDrawCommand> farToNear = Comparator
                .comparingDouble((GpuDrawCommand command) -> depth.applyAsDouble(command))
                .reversed()
                .thenComparingInt(GpuDrawCommand::firstIndex);
        for (List<GpuDrawCommand> group : groups) group.sort(farToNear);

        int specialStream = 10;
        int specialIndex = 0;
        List<GpuDrawCommand> special = groups.get(specialStream);
        if (special.isEmpty()) {
            specialStream = 11;
            special = groups.get(specialStream);
        }

        double pair12 = average(groups.get(1), groups.get(2), depth);
        double pair34 = average(groups.get(3), groups.get(4), depth);
        double pair68 = average(groups.get(6), groups.get(8), depth);
        List<GpuDrawCommand> result = new ArrayList<>(commands.size());
        for (int priority = 0; priority < 10; priority++) {
            // The legacy client only drains the priority-10/11 stream at
            // these three checkpoints.  Treating every priority as a
            // checkpoint changes ordering for priorities 1/2, 4, and 6-8.
            double threshold = checkpoint(priority, pair12, pair34, pair68);
            while (specialIndex < special.size()
                    && depth.applyAsDouble(special.get(specialIndex)) > threshold) {
                result.add(special.get(specialIndex++));
                if (specialIndex == special.size() && specialStream == 10 && !groups.get(11).isEmpty()) {
                    specialStream = 11;
                    special = groups.get(11);
                    specialIndex = 0;
                }
            }
            result.addAll(groups.get(priority));

            if (specialIndex == special.size() && specialStream == 10 && !groups.get(11).isEmpty()) {
                specialStream = 11;
                special = groups.get(11);
                specialIndex = 0;
            }
        }
        while (specialIndex < special.size()) result.add(special.get(specialIndex++));
        return List.copyOf(result);
    }

    private static double checkpoint(int priority, double pair12, double pair34, double pair68) {
        return switch (priority) {
            case 0 -> pair12;
            case 3 -> pair34;
            case 5 -> pair68;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    private static double average(List<GpuDrawCommand> first, List<GpuDrawCommand> second,
                                  ToDoubleFunction<GpuDrawCommand> depth) {
        int count = first.size() + second.size();
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (GpuDrawCommand command : first) sum += depth.applyAsDouble(command);
        for (GpuDrawCommand command : second) sum += depth.applyAsDouble(command);
        return sum / count;
    }
}
