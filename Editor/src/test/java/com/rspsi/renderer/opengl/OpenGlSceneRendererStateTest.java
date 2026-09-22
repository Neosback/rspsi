package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlSceneRendererStateTest {
    @Test
    void nativeCullValidationNeverCullsTerrain() {
        assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.TERRAIN, OpenGlSceneRenderer.CULL_FRONT_CCW));
        assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.TERRAIN, OpenGlSceneRenderer.CULL_FRONT_CW));
    }

    @Test
    void nativeCullValidationCullsModelLayersOnlyWhenEnabled() {
        assertFalse(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.GROUND_OBJECT, OpenGlSceneRenderer.CULL_OFF));
        assertTrue(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.GROUND_OBJECT, OpenGlSceneRenderer.CULL_FRONT_CCW));
        assertTrue(OpenGlSceneRenderer.cullEnabledFor(
                SceneLayer.Kind.WALL_DECORATION, OpenGlSceneRenderer.CULL_FRONT_CW));
    }
}
