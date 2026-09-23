package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
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

        // Reusable ranked slots retain primitive depth/tie values without
        // allocating boxed Double/Integer entries in identity maps every
        // frame. Slots only grow when a later scene contains more alpha
        // commands than any scene previously processed by this workspace.
        workspace.ensureSlots(commands.size());
        for (int index = 0; index < commands.size(); index++) {
            GpuDrawCommand command = commands.get(index);
            RankedCommand ranked = workspace.slots.get(index);
            ranked.command = command;
            ranked.depth = depth.applyAsDouble(command);
            ranked.tieBreaker = tieBreaker.applyAsInt(command);
            int priority = Math.max(0, Math.min(11, command.priority()));
            workspace.groups.get(priority).add(ranked);
        }
        workspace.usedSlots = commands.size();
        for (List<RankedCommand> group : workspace.groups) {
            group.sort(workspace.farToNear);
        }

        int specialStream = 10;
        int specialIndex = 0;
        List<RankedCommand> special = workspace.groups.get(specialStream);
        if (special.isEmpty()) {
            specialStream = 11;
            special = workspace.groups.get(specialStream);
        }

        double pair12 = average(workspace.groups.get(1), workspace.groups.get(2));
        double pair34 = average(workspace.groups.get(3), workspace.groups.get(4));
        double pair68 = average(workspace.groups.get(6), workspace.groups.get(8));
        for (int priority = 0; priority < 10; priority++) {
            double threshold = checkpoint(priority, pair12, pair34, pair68);
            while (specialIndex < special.size()
                    && special.get(specialIndex).depth > threshold) {
                workspace.result.add(special.get(specialIndex++).command);
                if (specialIndex == special.size()
                        && specialStream == 10
                        && !workspace.groups.get(11).isEmpty()) {
                    specialStream = 11;
                    special = workspace.groups.get(11);
                    specialIndex = 0;
                }
            }
            for (RankedCommand ranked : workspace.groups.get(priority)) {
                workspace.result.add(ranked.command);
            }

            if (specialIndex == special.size()
                    && specialStream == 10
                    && !workspace.groups.get(11).isEmpty()) {
                specialStream = 11;
                special = workspace.groups.get(11);
                specialIndex = 0;
            }
        }
        while (specialIndex < special.size()) {
            workspace.result.add(special.get(specialIndex++).command);
        }
        return workspace.result;
    }

    public static final class Workspace {
        private final List<List<RankedCommand>> groups = new ArrayList<>(12);
        private final ArrayList<RankedCommand> slots = new ArrayList<>();
        private final ArrayList<GpuDrawCommand> result = new ArrayList<>();
        private final Comparator<RankedCommand> farToNear;
        private int usedSlots;

        public Workspace() {
            for (int index = 0; index < 12; index++) {
                groups.add(new ArrayList<>());
            }
            farToNear = Comparator
                    .comparingDouble((RankedCommand ranked) -> ranked.depth)
                    .reversed()
                    .thenComparingInt(ranked -> ranked.tieBreaker)
                    .thenComparingInt(ranked -> ranked.command.firstIndex());
        }

        private void ensureSlots(int size) {
            while (slots.size() < size) {
                slots.add(new RankedCommand());
            }
        }

        private void reset() {
            for (List<RankedCommand> group : groups) {
                group.clear();
            }
            result.clear();
            for (int index = 0; index < usedSlots; index++) {
                RankedCommand ranked = slots.get(index);
                ranked.command = null;
                ranked.depth = 0.0;
                ranked.tieBreaker = 0;
            }
            usedSlots = 0;
        }
    }

    private static final class RankedCommand {
        private GpuDrawCommand command;
        private double depth;
        private int tieBreaker;
    }

    private static double checkpoint(int priority, double pair12, double pair34, double pair68) {
        return switch (priority) {
            case 0 -> pair12;
            case 3 -> pair34;
            case 5 -> pair68;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    private static double average(List<RankedCommand> first,
                                  List<RankedCommand> second) {
        int count = first.size() + second.size();
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (RankedCommand command : first) sum += command.depth;
        for (RankedCommand command : second) sum += command.depth;
        return sum / count;
    }
}
