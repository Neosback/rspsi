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
        assertEquals(160.0f, controller.camera().y(), 0.0001f);
    }
}
