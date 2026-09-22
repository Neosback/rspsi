package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RenderOrderKeyTest {
    @Test
    void priorityDoesNotSplitIdenticalNativeDrawState() {
        GpuDrawCommand low = command(7, 1, 4, GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand high = command(7, 11, 4, GpuDrawCommand.RenderMode.DEFAULT);

        assertEquals(RenderOrderKey.nativeState(low), RenderOrderKey.nativeState(high));
    }

    @Test
    void materialLayerBiasAndDepthModeChangeNativeState() {
        GpuDrawCommand base = command(7, 1, 4, GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand texture = command(9, 1, 4, GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand bias = command(7, 1, 5, GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand depth = command(7, 1, 4, GpuDrawCommand.RenderMode.SORTED_NO_DEPTH);
        GpuDrawCommand layer = new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.WALL_DECORATION,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, 7, 1, 4, 1, GpuDrawCommand.RenderMode.DEFAULT);

        assertNotEquals(RenderOrderKey.nativeState(base), RenderOrderKey.nativeState(texture));
        assertNotEquals(RenderOrderKey.nativeState(base), RenderOrderKey.nativeState(bias));
        assertNotEquals(RenderOrderKey.nativeState(base), RenderOrderKey.nativeState(depth));
        assertNotEquals(RenderOrderKey.nativeState(base), RenderOrderKey.nativeState(layer));
    }

    private static GpuDrawCommand command(int textureId, int priority, int bias,
                                          GpuDrawCommand.RenderMode mode) {
        return new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, textureId, priority, bias, 1, mode);
    }
}
