package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuDrawBatchPlannerTest {
    @Test
    void groupsOnlySameStateCommandsInsideTheSameZone() {
        WorldTileAddress zoneA0 = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress zoneA1 = WorldTileAddress.of(3201, 3200, 0);
        WorldTileAddress zoneB = WorldTileAddress.of(3208, 3200, 0);

        List<GpuDrawCommand> commands = List.of(
                command(zoneA0, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 0),
                // Different authored priority is still the same native state.
                command(zoneA1, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 3),
                // Texture identity is resident per vertex and no longer splits the batch.
                command(zoneA1, GpuDrawCommand.SubmissionPass.OPAQUE, 9, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 3),
                command(zoneA1, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 4),
                // This test supplies logical zone keys, so crossing zones still splits here.
                command(zoneB, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 4),
                // Depth mode/state changes also split the range.
                command(zoneB, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.SORTED_NO_DEPTH, 4)
        );

        List<GpuDrawBatchPlanner.Batch> batches = GpuDrawBatchPlanner.plan(
                commands, List.of(0, 1, 2, 3, 4, 5),
                GpuDrawCommand.SubmissionPass.OPAQUE,
                index -> zoneKey(commands.get(index).tile()));

        assertEquals(3, batches.size());
        assertEquals(List.of(0, 1, 2, 3), batches.get(0).commandIndices());
        assertEquals(List.of(4), batches.get(1).commandIndices());
        assertEquals(List.of(5), batches.get(2).commandIndices());
    }

    @Test
    void cursorExposesTheSameBatchesWithoutMaterializingNestedLists() {
        WorldTileAddress zoneA0 = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress zoneA1 = WorldTileAddress.of(3201, 3200, 0);
        WorldTileAddress zoneB = WorldTileAddress.of(3208, 3200, 0);
        List<GpuDrawCommand> commands = List.of(
                command(zoneA0, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 0),
                command(zoneA1, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 3),
                command(zoneA1, GpuDrawCommand.SubmissionPass.OPAQUE, 9, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 3),
                command(zoneB, GpuDrawCommand.SubmissionPass.OPAQUE, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 4));

        GpuDrawBatchPlanner.BatchCursor cursor = GpuDrawBatchPlanner.cursor(
                commands, List.of(0, 1, 2, 3),
                GpuDrawCommand.SubmissionPass.OPAQUE,
                index -> zoneKey(commands.get(index).tile()));

        assertEquals(true, cursor.next());
        assertEquals(3, cursor.commandCount());
        assertEquals(0, cursor.orderedStart());
        assertEquals(0, cursor.firstCommandIndex());
        assertEquals(1, cursor.commandIndexAt(1));
        assertEquals(2, cursor.commandIndexAt(2));

        assertEquals(true, cursor.next());
        assertEquals(1, cursor.commandCount());
        assertEquals(3, cursor.orderedStart());
        assertEquals(3, cursor.firstCommandIndex());

        assertEquals(false, cursor.next());
    }

    @Test
    void rejectsCommandsFromTheWrongSubmissionPass() {
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);
        List<GpuDrawCommand> commands = List.of(
                command(tile, GpuDrawCommand.SubmissionPass.ALPHA, 7, 0,
                        GpuDrawCommand.RenderMode.DEFAULT, 0));

        assertThrows(IllegalArgumentException.class, () -> GpuDrawBatchPlanner.plan(
                commands, List.of(0), GpuDrawCommand.SubmissionPass.OPAQUE,
                index -> zoneKey(commands.get(index).tile())));
    }

    private static GpuDrawCommand command(WorldTileAddress tile,
                                          GpuDrawCommand.SubmissionPass pass,
                                          int textureId,
                                          int depthBias,
                                          GpuDrawCommand.RenderMode mode,
                                          int priority) {
        return new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT, pass,
                0, 3, textureId, priority, depthBias, 42, mode);
    }

    private static long zoneKey(WorldTileAddress tile) {
        return (((long) tile.plane()) << 32)
                | (((long) ((tile.worldX() >> 3) & 0xFFFF)) << 16)
                | ((long) ((tile.worldY() >> 3) & 0xFFFF));
    }
}
