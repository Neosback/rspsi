package com.rspsi.studio;

import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.viewport.SurfaceHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSceneViewportTest {

    @Test
    void managesSelectionAndExposesNavigation() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        assertNotNull(viewport.navigation(), "navigation controller must not be null");
        assertFalse(viewport.selection().isPresent(), "initial selection should be empty");

        PickResult hit = new PickResult(new WorldTile(1, 3200, 3200), 1, 1050, 42.0f);
        viewport.setSelection(hit);

        assertTrue(viewport.selection().isPresent());
        assertEquals(1050, viewport.selection().get().objectId());
        assertEquals(1, viewport.selection().get().plane());
        assertEquals(3200, viewport.selection().get().tile().x());

        viewport.clearSelection();
        assertFalse(viewport.selection().isPresent());
    }

    @Test
    void convertsRendererPickIntoSemanticSurfaceHit() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        WorldTile tile = new WorldTile(0, 3200, 3201);
        WorldObject placement = new WorldObject(1050, 10, 2, 0, 4, 5);
        viewport.setObjectResolver((anchor, objectId) ->
                objectId == placement.id()
                        ? java.util.Optional.of(placement)
                        : java.util.Optional.empty());

        SurfaceHit objectHit = viewport.semanticHit(
                new PickResult(tile, 0, placement.id(), 12.0f));

        assertTrue(objectHit.objectHit());
        assertEquals(tile, objectHit.tile());
        assertEquals(tile, objectHit.targetTile());
        assertEquals(placement.id(), objectHit.objectId());
        assertEquals(placement, objectHit.object().orElseThrow());

        SurfaceHit terrainHit = viewport.semanticHit(new PickResult(tile, 0));
        assertFalse(terrainHit.objectHit());
        assertEquals(tile, terrainHit.targetTile());
        assertTrue(terrainHit.object().isEmpty());
    }

    @Test
    void frameSelectionUpdatesNavigationCamera() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        viewport.navigation().frameSelection(1000.0f, -500.0f, 2000.0f);

        assertEquals(1000.0f, viewport.navigation().camera().x(), 0.001f);
        assertEquals(-2100.0f, viewport.navigation().camera().y(), 0.001f);
        assertEquals(-400.0f, viewport.navigation().camera().z(), 0.001f);
    }

    @Test
    void presentedFrameCameraDoesNotFollowNavigationUntilNextFrame() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        CameraState renderedCamera = new CameraState(
                3200.0f, -2400.0f, -4200.0f,
                (float) -Math.toRadians(28.0), 0.0f);
        SceneCameraProjection projection = SceneCameraProjection.editorDefault();

        viewport.recordPresentedFrame(null, renderedCamera, projection, 1280, 720);
        viewport.setCamera(new CameraState(
                3600.0f, -1800.0f, -3900.0f,
                (float) -Math.toRadians(20.0), (float) Math.toRadians(35.0)));

        assertEquals(renderedCamera, viewport.lastFrameCamera(),
                "presented overlays/picks must retain the camera that produced the scene image");
        assertEquals(projection, viewport.lastFrameProjection());
        assertEquals(1280, viewport.lastWidth());
        assertEquals(720, viewport.lastHeight());
    }

    @Test
    void rejectsInvalidPresentedFrameDimensions() {
        NativeSceneViewport viewport = new NativeSceneViewport();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> viewport.recordPresentedFrame(
                        null,
                        new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                        SceneCameraProjection.editorDefault(),
                        0,
                        720));
    }

    @Test
    void mapsNeutralCullingValidationModesWithoutInitializingOpenGl() {
        NativeSceneViewport viewport = new NativeSceneViewport();

        viewport.setCullMode(com.rspsi.editor.render.BackfacePolicy.NativeCullingMode.TWO_SIDED);
        assertEquals(com.rspsi.renderer.opengl.OpenGlSceneRenderer.CULL_OFF, viewport.cullMode());

        viewport.setCullMode(com.rspsi.editor.render.BackfacePolicy.NativeCullingMode.CLIENT_FRONT);
        assertEquals(com.rspsi.renderer.opengl.OpenGlSceneRenderer.CULL_FRONT_CCW, viewport.cullMode());

        viewport.setCullMode(com.rspsi.editor.render.BackfacePolicy.NativeCullingMode.REVERSED_DEBUG);
        assertEquals(com.rspsi.renderer.opengl.OpenGlSceneRenderer.CULL_FRONT_CW, viewport.cullMode());
    }

}
