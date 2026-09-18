package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

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
