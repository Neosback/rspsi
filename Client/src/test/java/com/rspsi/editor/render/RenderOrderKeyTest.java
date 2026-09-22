package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderOrderKeyTest {
    @Test
    void capturesLayerFacePriorityDepthModeAndBias() {
        GpuDrawCommand command = new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.WALL_DECORATION,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, 7, 11, 23, 899,
                GpuDrawCommand.RenderMode.SORTED_NO_DEPTH);

        RenderOrderKey key = RenderOrderKey.from(command);

        assertEquals(SceneLayer.Kind.WALL_DECORATION.ordinal(), key.modelPriority());
        assertEquals(11, key.facePriority());
        assertEquals(GpuDrawCommand.RenderMode.SORTED_NO_DEPTH, key.depthMode());
        assertEquals(23, key.faceBias());
    }

    @Test
    void facePriorityChangesOrderingWithoutForcingANewNativeStateBatch() {
        RenderOrderKey low = RenderOrderKey.from(command(1, 4, GpuDrawCommand.RenderMode.DEFAULT));
        RenderOrderKey high = RenderOrderKey.from(command(11, 4, GpuDrawCommand.RenderMode.DEFAULT));
        RenderOrderKey differentBias = RenderOrderKey.from(command(11, 5, GpuDrawCommand.RenderMode.DEFAULT));
        RenderOrderKey differentDepth = RenderOrderKey.from(command(11, 4, GpuDrawCommand.RenderMode.SORTED_NO_DEPTH));

        assertTrue(low.sameNativeState(high));
        assertFalse(low.sameNativeState(differentBias));
        assertFalse(low.sameNativeState(differentDepth));
    }

    private static GpuDrawCommand command(int priority, int bias, GpuDrawCommand.RenderMode mode) {
        return new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, priority, bias, 1, mode);
    }
}
