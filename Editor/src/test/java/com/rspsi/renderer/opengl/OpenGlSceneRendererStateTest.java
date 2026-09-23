package com.rspsi.renderer.opengl;

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
