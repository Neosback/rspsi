package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuCommandVisibility;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenGlSceneRendererAlphaOrderCacheTest {
    @Test
    void reusesStableAlphaOrderAndInvalidatesOnCameraOrVisibilityChange() {
        GpuUploadPlan plan = alphaPlan();
        CameraState camera = new CameraState(0, 64, 0, 0, 0);
        GpuCommandVisibility visibility = GpuCommandVisibility.of(plan, camera);
        OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();

        List<Integer> first = renderer.alphaOrderFor(
                plan, plan.commands(), visibility, camera);
        List<Integer> stable = renderer.alphaOrderFor(
                plan, plan.commands(), visibility, camera);
        List<Integer> moved = renderer.alphaOrderFor(
                plan, plan.commands(), visibility,
                new CameraState(128, 64, 0, 0, 0));
        GpuCommandVisibility replacementVisibility =
                GpuCommandVisibility.of(plan, camera);
        List<Integer> replacedVisibility = renderer.alphaOrderFor(
                plan, plan.commands(), replacementVisibility, camera);

        assertSame(first, stable);
        assertNotSame(first, moved);
        assertNotSame(moved, replacedVisibility);
        assertEquals(2, first.size());
    }

    private static GpuUploadPlan alphaPlan() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(256, 0, 64), vertex(320, 0, 64), vertex(256, 64, 64),
                vertex(128, 0, 32), vertex(192, 0, 32), vertex(128, 64, 32));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        List<GpuDrawCommand> commands = List.of(
                new GpuDrawCommand(
                        WorldTileAddress.of(2, 0, 0),
                        SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.ALPHA,
                        0, 3, -1, 0, 1),
                new GpuDrawCommand(
                        WorldTileAddress.of(1, 0, 0),
                        SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.ALPHA,
                        3, 3, -1, 10, 2));
        return new GpuUploadPlan(
                vertices, indices, commands, List.of(), Map.of(), List.of(),
                "alpha-order-cache-fixture");
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(
                x, y, z, 0, 0, 0,
                GpuColorEncoding.PACKED_JAGEX_HSL,
                0, 0, 0, 0, 0,
                -1, 255, 0, 0, 0, 0, 0);
    }
}
