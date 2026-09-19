package com.rspsi.studio;

import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.ScreenPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportOverlayProjectionTest {
    private static final SceneCameraProjection PROJECTION = SceneCameraProjection.editorDefault();

    @Test
    void testCenterScreenProjection() {
        CameraState camera = new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        ScreenPoint sp = ViewportOverlayDraw.project(
                0.0f, 0.0f, 1000.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);

        assertTrue(sp.visible(), "target ahead of camera should be visible");
        assertEquals(400.0f, sp.x(), 0.01f);
        assertEquals(300.0f, sp.y(), 0.01f);
    }

    @Test
    void testPointsBehindCameraAreCullable() {
        CameraState camera = new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        ScreenPoint sp = ViewportOverlayDraw.project(
                0.0f, 0.0f, -500.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);

        assertFalse(sp.visible(), "points behind camera must be culled");
    }

    @Test
    void testOriginOffsetAppliesToScreen() {
        CameraState camera = new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        ScreenPoint sp = ViewportOverlayDraw.project(
                0.0f, 0.0f, 1000.0f,
                100.0f, 50.0f, 800, 600,
                camera, PROJECTION);

        assertTrue(sp.visible());
        assertEquals(500.0f, sp.x(), 0.01f);
        assertEquals(350.0f, sp.y(), 0.01f);
    }

    @Test
    void testHorizontalSymmetry() {
        CameraState camera = new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        ScreenPoint right = ViewportOverlayDraw.project(
                200.0f, 0.0f, 1000.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);
        ScreenPoint left = ViewportOverlayDraw.project(
                -200.0f, 0.0f, 1000.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);

        assertTrue(right.visible());
        assertTrue(left.visible());
        assertTrue(right.x() > 400.0f);
        assertTrue(left.x() < 400.0f);
        assertEquals(right.x() - 400.0f, 400.0f - left.x(), 0.01f);
        assertEquals(300.0f, right.y(), 0.01f);
        assertEquals(300.0f, left.y(), 0.01f);
    }

    @Test
    void testVerticalProjectionInOsrsNegativeUpConvention() {
        CameraState camera = new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        // Negative Y is up in OSRS canonical space
        ScreenPoint high = ViewportOverlayDraw.project(
                0.0f, -200.0f, 1000.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);
        ScreenPoint low = ViewportOverlayDraw.project(
                0.0f, 200.0f, 1000.0f,
                0.0f, 0.0f, 800, 600,
                camera, PROJECTION);

        assertTrue(high.visible());
        assertTrue(low.visible());
        // Higher point should appear toward top of screen (smaller Y)
        assertTrue(high.y() < 300.0f);
        // Lower point should appear toward bottom of screen (larger Y)
        assertTrue(low.y() > 300.0f);
        assertEquals(300.0f - high.y(), low.y() - 300.0f, 0.01f);
    }

    @Test
    void testRoundTripRayConsistencyWithPicker() {
        CameraState camera = new CameraState(3200.0f, -2400.0f, -4200.0f,
                (float) -Math.toRadians(28.0), (float) Math.toRadians(45.0));
        int width = 1280;
        int height = 720;

        float targetX = 3500.0f;
        float targetY = -2000.0f;
        float targetZ = -3800.0f;

        ScreenPoint sp = ViewportOverlayDraw.project(
                targetX, targetY, targetZ,
                0.0f, 0.0f, width, height,
                camera, PROJECTION);

        assertTrue(sp.visible(), "target coordinate should be visible");

        // Now reverse-project (sx, sy) via ray formulation
        float focal = (float) ((height * 0.5) / Math.tan(PROJECTION.verticalFieldOfView() * 0.5));
        float cameraX = (sp.x() - width * 0.5f) / focal;
        float cameraY = (height * 0.5f - sp.y()) / focal;
        float yawDepth = -cameraY * (float) Math.sin(camera.pitch())
                + (float) Math.cos(camera.pitch());
        float worldY = -(cameraY * (float) Math.cos(camera.pitch())
                + (float) Math.sin(camera.pitch()));
        float worldX = cameraX * (float) Math.cos(camera.yaw())
                + yawDepth * (float) Math.sin(camera.yaw());
        float worldZ = -cameraX * (float) Math.sin(camera.yaw())
                + yawDepth * (float) Math.cos(camera.yaw());

        // Direction vector from camera to target
        float dx = targetX - camera.x();
        float dy = targetY - camera.y();
        float dz = targetZ - camera.z();
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float normDx = dx / len;
        float normDy = dy / len;
        float normDz = dz / len;

        // Normalized ray direction
        float rLen = (float) Math.sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ);
        float normRx = worldX / rLen;
        float normRy = worldY / rLen;
        float normRz = worldZ / rLen;

        assertEquals(normDx, normRx, 0.001f, "ray direction X must match target direction");
        assertEquals(normDy, normRy, 0.001f, "ray direction Y must match target direction");
        assertEquals(normDz, normRz, 0.001f, "ray direction Z must match target direction");
    }
}
