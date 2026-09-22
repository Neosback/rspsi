package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewportControllerTest {
    @Test
    void panOrbitAndZoomUpdateTheRendererNeutralCamera() {
        ViewportController controller = new ViewportController(
                new CameraState(100.0f, 200.0f, 300.0f, 0.0f, 0.0f));

        controller.update(2.0f, 3.0f, true, false, 0.0f);
        assertEquals(84.0f, controller.camera().x());
        assertEquals(324.0f, controller.camera().z());
        controller.update(5.0f, -5.0f, false, true, 1.0f);

        assertEquals(-0.04f, controller.camera().pitch(), 0.0001f);
        assertEquals(0.04f, controller.camera().yaw(), 0.0001f);
        // Zoom dollies forward along the heading: Y is preserved so camera does not dive into terrain
        assertEquals(200.0f, controller.camera().y(), 0.0001f);
        // And moves forward along heading: deltaX = sin(0.04) * 180 ~ 7.198f, deltaZ = cos(0.04) * 180 ~ 179.85f
        assertEquals(91.198f, controller.camera().x(), 0.05f);
        assertEquals(503.85f, controller.camera().z(), 0.05f);
    }

    @Test
    void verticalMovementElevatesCameraAlongSceneY() {
        ViewportController controller = new ViewportController(
                new CameraState(100.0f, 200.0f, 300.0f, 0.0f, 0.0f));

        // Negative distance moves up into sky (more negative Y)
        controller.moveVertical(-50.0f);
        assertEquals(150.0f, controller.camera().y(), 0.0001f);
        // Positive distance descends towards ground (more positive Y)
        controller.moveVertical(30.0f);
        assertEquals(180.0f, controller.camera().y(), 0.0001f);
    }

    @Test
    void forwardMovementFollowsTheHeadingAndLeavesHeightAlone() {
        // yaw 0 faces +z: the renderer's forward axis is (sin yaw, cos yaw).
        ViewportController north = new ViewportController(
                new CameraState(100.0f, -50.0f, 300.0f, -0.4f, 0.0f));
        north.moveForward(10.0f);
        assertEquals(100.0f, north.camera().x(), 0.0001f);
        assertEquals(310.0f, north.camera().z(), 0.0001f);
        // Pitch must not turn forward motion into a dive.
        assertEquals(-50.0f, north.camera().y(), 0.0001f);

        // A quarter turn faces +x.
        ViewportController east = new ViewportController(
                new CameraState(100.0f, -50.0f, 300.0f, 0.0f, (float) Math.PI / 2.0f));
        east.moveForward(10.0f);
        assertEquals(110.0f, east.camera().x(), 0.0001f);
        assertEquals(300.0f, east.camera().z(), 0.0001f);

        east.moveForward(-10.0f);
        assertEquals(100.0f, east.camera().x(), 0.0001f);
    }

    @Test
    void rotatingTurnsInPlaceRatherThanSlidingSideways() {
        ViewportController controller = new ViewportController(
                new CameraState(100.0f, -50.0f, 300.0f, -0.3f, 0.0f));

        controller.rotateYaw(0.5f);

        assertEquals(0.5f, controller.camera().yaw(), 0.0001f);
        assertEquals(100.0f, controller.camera().x(), 0.0001f);
        assertEquals(-50.0f, controller.camera().y(), 0.0001f);
        assertEquals(300.0f, controller.camera().z(), 0.0001f);
        assertEquals(-0.3f, controller.camera().pitch(), 0.0001f);
    }
}
