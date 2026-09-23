package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlSceneRendererStateTest {
    @Test
    void depthParticipatingModesUseOpaqueAndAlphaPassStateLikeRuneLite() {
        for (GpuDrawCommand.RenderMode mode : new GpuDrawCommand.RenderMode[]{
                GpuDrawCommand.RenderMode.DEFAULT,
                GpuDrawCommand.RenderMode.SORTED,
                GpuDrawCommand.RenderMode.UNSORTED}) {
            OpenGlSceneRenderer.NativeDrawState opaque = OpenGlSceneRenderer.nativeDrawState(
                    GpuDrawCommand.SubmissionPass.OPAQUE, mode);
            assertTrue(opaque.depthTest());
            assertTrue(opaque.depthWrite());
            assertFalse(opaque.blend());

            OpenGlSceneRenderer.NativeDrawState alpha = OpenGlSceneRenderer.nativeDrawState(
                    GpuDrawCommand.SubmissionPass.ALPHA, mode);
            assertTrue(alpha.depthTest());
            assertFalse(alpha.depthWrite());
            assertTrue(alpha.blend());
        }
    }

    @Test
    void noDepthModesNeverReadOrWriteDepthButStillRespectAlphaBlending() {
        for (GpuDrawCommand.RenderMode mode : new GpuDrawCommand.RenderMode[]{
                GpuDrawCommand.RenderMode.SORTED_NO_DEPTH,
                GpuDrawCommand.RenderMode.UNSORTED_NO_DEPTH}) {
            OpenGlSceneRenderer.NativeDrawState opaque = OpenGlSceneRenderer.nativeDrawState(
                    GpuDrawCommand.SubmissionPass.OPAQUE, mode);
            assertFalse(opaque.depthTest());
            assertFalse(opaque.depthWrite());
            assertFalse(opaque.blend());

            OpenGlSceneRenderer.NativeDrawState alpha = OpenGlSceneRenderer.nativeDrawState(
                    GpuDrawCommand.SubmissionPass.ALPHA, mode);
            assertFalse(alpha.depthTest());
            assertFalse(alpha.depthWrite());
            assertTrue(alpha.blend());
        }
    }

    @Test
    void frameMetricsAccumulateSubmissionCountsWithoutASecondSceneWalk() {
        OpenGlSceneRenderer.FrameMetrics metrics = new OpenGlSceneRenderer.FrameMetrics();
        GpuDrawCommand terrain = new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0), SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 6, -1, 0, -1);
        GpuDrawCommand object = new GpuDrawCommand(
                WorldTileAddress.of(3201, 3200, 0), SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.ALPHA, 6, 9, -1, 0, 42);

        metrics.record(terrain);
        metrics.record(object);

        assertEquals(15, metrics.renderedIndices());
        assertEquals(2, metrics.terrainTriangles());
        assertEquals(3, metrics.objectTriangles());

        metrics.reset();
        assertEquals(0, metrics.renderedIndices());
        assertEquals(0, metrics.terrainTriangles());
        assertEquals(0, metrics.objectTriangles());
    }

    @Test
    void clientFrontCullingIsTheRendererDefault() {
        assertEquals(OpenGlSceneRenderer.CULL_FRONT_CCW,
                new OpenGlSceneRenderer().cullMode());
    }

    @Test
    void nativeCullValidationNeverCullsTerrain() {
        assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.TERRAIN, OpenGlSceneRenderer.CULL_FRONT_CCW));
        assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.TERRAIN, OpenGlSceneRenderer.CULL_FRONT_CW));
    }

    @Test
    void nativeCullValidationCullsEveryModelLayerOnlyWhenEnabled() {
        for (SceneLayer.Kind layer : SceneLayer.Kind.values()) {
            if (layer == SceneLayer.Kind.TERRAIN) continue;
            assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                    layer, OpenGlSceneRenderer.CULL_OFF));
            assertTrue(OpenGlSceneRenderer.cullEnabledFor(
                    layer, OpenGlSceneRenderer.CULL_FRONT_CCW));
            assertTrue(OpenGlSceneRenderer.cullEnabledFor(
                    layer, OpenGlSceneRenderer.CULL_FRONT_CW));
        }
    }
}
