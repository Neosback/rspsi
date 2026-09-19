package com.rspsi.editor.render;

import java.util.Objects;

/** Renderer-neutral orbit, pan, and zoom controller for a scene viewport. */
public final class ViewportController {
    private CameraState camera;

    public ViewportController(CameraState initialCamera) {
        camera = Objects.requireNonNull(initialCamera, "initial camera");
    }

    public CameraState camera() {
        return camera;
    }

    public void setCamera(CameraState next) {
        camera = Objects.requireNonNull(next, "camera");
    }

    /**
     * Drives the camera forward along the direction it faces, in the ground
     * plane. The renderer's forward axis is {@code (sin(yaw), cos(yaw))} in
     * world x/z (see the vertex shader's
     * {@code forward = d.x * sin(yaw) + d.z * cos(yaw)}), so walking the
     * camera along that vector moves it toward whatever is on screen ahead.
     *
     * <p>Pitch is deliberately ignored: a map editor camera that is angled
     * down should travel across the map at a steady height rather than fly
     * into the terrain, so this is a heading-only move, not a free-fly along
     * the view vector.</p>
     *
     * @param distance world units to advance; negative moves backward
     */
    public void moveForward(float distance) {
        if (distance == 0.0f) return;
        float sinYaw = (float) Math.sin(camera.yaw());
        float cosYaw = (float) Math.cos(camera.yaw());
        camera = new CameraState(camera.x() + distance * sinYaw, camera.y(),
                camera.z() + distance * cosYaw, camera.pitch(), camera.yaw());
    }

    /**
     * Turns the camera in place. This is a heading change, not a sideways
     * slide: the camera position is untouched and only {@code yaw} moves.
     *
     * @param radians positive turns the view to the right
     */
    public void rotateYaw(float radians) {
        if (radians == 0.0f) return;
        camera = new CameraState(camera.x(), camera.y(), camera.z(),
                camera.pitch(), camera.yaw() + radians);
    }

    /** Applies one frame of input; deltas are pixels and wheel is ImGui-style. */
    public void update(float deltaX, float deltaY,
                       boolean pan, boolean orbit, float wheel) {
        if (pan) {
            camera = new CameraState(
                    camera.x() - deltaX * 8.0f,
                    camera.y(),
                    camera.z() + deltaY * 8.0f,
                    camera.pitch(), camera.yaw());
        }
        if (orbit) {
            float pitch = Math.max((float) -Math.PI / 2.0f + 0.05f,
                    Math.min((float) Math.PI / 2.0f - 0.05f,
                            camera.pitch() + deltaY * 0.008f));
            camera = new CameraState(camera.x(), camera.y(), camera.z(),
                    pitch, camera.yaw() + deltaX * 0.008f);
        }
        if (wheel != 0.0f) {
            float zoom = wheel * 180.0f;
            camera = new CameraState(camera.x(),
                    // Keep zooming along the OSRS down-axis. A positive wheel
                    // delta moves the camera farther above the scene (more
                    // negative Y); clamping to positive Y inverted the scene
                    // once the canonical negative-up convention was restored.
                    camera.y() - zoom,
                    camera.z() + zoom, camera.pitch(), camera.yaw());
        }
    }
}
