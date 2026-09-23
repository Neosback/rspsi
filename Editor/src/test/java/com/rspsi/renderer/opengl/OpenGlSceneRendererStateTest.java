package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlSceneRendererStateTest {
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
