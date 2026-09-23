package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

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
        return orderAlpha(commands, depth, ignored -> 0);
    }

    /**
     * Orders alpha ranges with a stable backend-neutral tie breaker for scene
     * rules such as shape-8 wall-decoration primary/secondary ordering.
     */
    public static List<GpuDrawCommand> orderAlpha(List<GpuDrawCommand> commands,
                                                   ToDoubleFunction<GpuDrawCommand> depth,
                                                   ToIntFunction<GpuDrawCommand> tieBreaker) {
        return List.copyOf(orderAlphaReusable(
                commands, depth, tieBreaker, new Workspace()));
    }

    /**
     * Reuses all planner collections across frames.
     *
     * <p>The returned list is owned by {@code workspace} and is only stable
     * until the next call using that workspace. Render loops that consume the
     * order immediately should prefer this method to avoid allocating twelve
     * priority lists, two identity maps, comparator chains, and a result list
     * every frame.</p>
     */
    public static List<GpuDrawCommand> orderAlphaReusable(
            List<GpuDrawCommand> commands,
            ToDoubleFunction<GpuDrawCommand> depth,
            ToIntFunction<GpuDrawCommand> tieBreaker,
            Workspace workspace) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(tieBreaker, "tieBreaker");
        Objects.requireNonNull(workspace, "workspace");
        workspace.reset();

        if (commands.size() < 2) {
            workspace.result.addAll(commands);
            return workspace.result;
        }

        // Comparator callbacks are intentionally memoized once per command.
        // The native depth function can consult geometry bounds, and Java's
        // TimSort may call a comparator O(n log n) times. Re-running scene
        // geometry work from the comparator is both unnecessary and capable
        // of amplifying a cache miss into a render-thread stall.
        for (GpuDrawCommand command : commands) {
            int priority = Math.max(0, Math.min(11, command.priority()));
            workspace.groups.get(priority).add(command);
            workspace.depths.put(command, depth.applyAsDouble(command));
            workspace.tieBreakers.put(command, tieBreaker.applyAsInt(command));
        }
        for (List<GpuDrawCommand> group : workspace.groups) {
            group.sort(workspace.farToNear);
        }

        int specialStream = 10;
        int specialIndex = 0;
        List<GpuDrawCommand> special = workspace.groups.get(specialStream);
        if (special.isEmpty()) {
            specialStream = 11;
            special = workspace.groups.get(specialStream);
        }

        double pair12 = average(workspace.groups.get(1), workspace.groups.get(2),
                workspace.depths);
        double pair34 = average(workspace.groups.get(3), workspace.groups.get(4),
                workspace.depths);
        double pair68 = average(workspace.groups.get(6), workspace.groups.get(8),
                workspace.depths);
        for (int priority = 0; priority < 10; priority++) {
            // The legacy client only drains the priority-10/11 stream at
            // these three checkpoints. Treating every priority as a
            // checkpoint changes ordering for priorities 1/2, 4, and 6-8.
            double threshold = checkpoint(priority, pair12, pair34, pair68);
            while (specialIndex < special.size()
                    && workspace.depths.get(special.get(specialIndex)) > threshold) {
                workspace.result.add(special.get(specialIndex++));
                if (specialIndex == special.size()
                        && specialStream == 10
                        && !workspace.groups.get(11).isEmpty()) {
                    specialStream = 11;
                    special = workspace.groups.get(11);
                    specialIndex = 0;
                }
            }
            workspace.result.addAll(workspace.groups.get(priority));

            if (specialIndex == special.size()
                    && specialStream == 10
                    && !workspace.groups.get(11).isEmpty()) {
                specialStream = 11;
                special = workspace.groups.get(11);
                specialIndex = 0;
            }
        }
        while (specialIndex < special.size()) {
            workspace.result.add(special.get(specialIndex++));
        }
        return workspace.result;
    }

    public static final class Workspace {
        private final List<List<GpuDrawCommand>> groups = new ArrayList<>(12);
        private final IdentityHashMap<GpuDrawCommand, Double> depths =
                new IdentityHashMap<>();
        private final IdentityHashMap<GpuDrawCommand, Integer> tieBreakers =
                new IdentityHashMap<>();
        private final ArrayList<GpuDrawCommand> result = new ArrayList<>();
        private final Comparator<GpuDrawCommand> farToNear;

        public Workspace() {
            for (int index = 0; index < 12; index++) {
                groups.add(new ArrayList<>());
            }
            farToNear = Comparator
                    .comparingDouble((GpuDrawCommand command) -> depths.get(command))
                    .reversed()
                    .thenComparingInt(command -> tieBreakers.get(command))
                    .thenComparingInt(GpuDrawCommand::firstIndex);
        }

        private void reset() {
            for (List<GpuDrawCommand> group : groups) {
                group.clear();
            }
            depths.clear();
            tieBreakers.clear();
            result.clear();
        }
    }

    private static double checkpoint(int priority, double pair12, double pair34, double pair68) {
        return switch (priority) {
            case 0 -> pair12;
            case 3 -> pair34;
            case 5 -> pair68;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    private static double average(List<GpuDrawCommand> first,
                                  List<GpuDrawCommand> second,
                                  Map<GpuDrawCommand, Double> depths) {
        int count = first.size() + second.size();
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (GpuDrawCommand command : first) sum += depths.get(command);
        for (GpuDrawCommand command : second) sum += depths.get(command);
        return sum / count;
    }
}
