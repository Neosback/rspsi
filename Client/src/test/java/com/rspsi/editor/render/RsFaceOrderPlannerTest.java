package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RsFaceOrderPlannerTest {
    @Test
    void interleavesSpecialPriorityStreamBeforeLowPriorityGroups() {
        GpuDrawCommand low = command(0, 1);
        GpuDrawCommand special = command(10, 2);
        GpuDrawCommand second = command(2, 3);

        List<GpuDrawCommand> ordered = RsFaceOrderPlanner.orderAlpha(
                List.of(low, special, second), value -> switch (value.priority()) {
                    case 10 -> 20.0;
                    case 2 -> 5.0;
                    default -> 0.0;
                });

        assertIterableEquals(List.of(special, low, second), ordered);
        assertEquals(3, ordered.stream().map(GpuDrawCommand::firstIndex).distinct().count());
    }

    @Test
    void evaluatesDepthAndTieBreakerOnlyOncePerCommand() {
        List<GpuDrawCommand> commands = List.of(
                command(0, 1), command(0, 2), command(1, 3),
                command(2, 4), command(10, 5), command(11, 6));
        java.util.IdentityHashMap<GpuDrawCommand, Integer> depthCalls =
                new java.util.IdentityHashMap<>();
        java.util.IdentityHashMap<GpuDrawCommand, Integer> tieCalls =
                new java.util.IdentityHashMap<>();

        RsFaceOrderPlanner.orderAlpha(
                commands,
                value -> {
                    depthCalls.merge(value, 1, Integer::sum);
                    return value.firstIndex();
                },
                value -> {
                    tieCalls.merge(value, 1, Integer::sum);
                    return value.priority();
                });

        for (GpuDrawCommand command : commands) {
            assertEquals(1, depthCalls.get(command));
            assertEquals(1, tieCalls.get(command));
        }
    }

    @Test
    void reusableWorkspaceRetainsItsResultContainerAcrossFrames() {
        GpuDrawCommand low = command(0, 1);
        GpuDrawCommand special = command(10, 2);
        GpuDrawCommand second = command(2, 3);
        RsFaceOrderPlanner.Workspace workspace = new RsFaceOrderPlanner.Workspace();

        List<GpuDrawCommand> first = RsFaceOrderPlanner.orderAlphaReusable(
                List.of(low, special, second),
                value -> value.priority() == 10 ? 20.0
                        : value.priority() == 2 ? 5.0 : 0.0,
                ignored -> 0,
                workspace);
        assertIterableEquals(List.of(special, low, second), first);

        List<GpuDrawCommand> secondFrame = RsFaceOrderPlanner.orderAlphaReusable(
                List.of(second, low),
                value -> value.priority(),
                ignored -> 0,
                workspace);

        assertSame(first, secondFrame,
                "render-loop workspace should reuse the same result list");
        assertIterableEquals(List.of(low, second), secondFrame);
    }

    @Test
    void continuesWithPriorityElevenAtTheSameLegacyCheckpoint() {
        GpuDrawCommand priorityZero = command(0, 1);
        GpuDrawCommand priorityOne = command(1, 2);
        GpuDrawCommand priorityTen = command(10, 3);
        GpuDrawCommand priorityEleven = command(11, 4);

        List<GpuDrawCommand> ordered = RsFaceOrderPlanner.orderAlpha(
                List.of(priorityZero, priorityOne, priorityTen, priorityEleven),
                value -> switch (value.priority()) {
                    case 10 -> 20.0;
                    case 11 -> 19.0;
                    case 1 -> 0.0;
                    default -> 0.0;
                });

        assertIterableEquals(List.of(priorityTen, priorityEleven, priorityZero, priorityOne), ordered);
    }

    private static GpuDrawCommand command(int priority, int index) {
        return new GpuDrawCommand(WorldTileAddress.of(0, 0, 0), SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.ALPHA, index, 3, -1, priority, index);
    }
}
