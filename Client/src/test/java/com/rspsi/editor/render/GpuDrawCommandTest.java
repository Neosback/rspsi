package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDrawCommandTest {
    @Test
    void carriesRuneScapeNoDepthModesWithoutChangingCompatibilityDefaults() {
        GpuDrawCommand defaultCommand = command(GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand sortedNoDepth = command(GpuDrawCommand.RenderMode.SORTED_NO_DEPTH);
        GpuDrawCommand unsortedNoDepth = command(GpuDrawCommand.RenderMode.UNSORTED_NO_DEPTH);

        assertFalse(defaultCommand.renderMode().noDepth());
        assertTrue(defaultCommand.scenePlane() == defaultCommand.tile().plane());
        assertTrue(defaultCommand.planeCullLevel() == defaultCommand.tile().plane());
        assertTrue(sortedNoDepth.renderMode().noDepth());
        assertTrue(unsortedNoDepth.renderMode().noDepth());
    }

    @Test
    void terrainCommandsNeverMergeAcrossWorldZoneBoundary() {
        WorldTileAddress lastTileInZone = WorldTileAddress.of(7, 4, 0);
        WorldTileAddress firstTileNextZone = WorldTileAddress.of(8, 4, 0);
        GpuDrawCommand command = new GpuDrawCommand(
                lastTileInZone, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, -1, GpuDrawCommand.RenderMode.DEFAULT);

        assertFalse(command.canMerge(firstTileNextZone, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                -1, 0, 0, -1, 3, GpuDrawCommand.RenderMode.DEFAULT));
    }

    private static GpuDrawCommand command(GpuDrawCommand.RenderMode mode) {
        return new GpuDrawCommand(WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 1, mode);
    }
}
