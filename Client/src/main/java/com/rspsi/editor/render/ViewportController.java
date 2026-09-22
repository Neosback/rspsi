package com.rspsi.editor.render;

import com.rspsi.editor.input.EditorKeyEvent;

import java.util.Objects;

/**
 * Renderer-neutral orbit, pan, zoom, and projection controller for a scene viewport.
 */
public final class ViewportController {
    public enum ProjectionMode {
        PERSPECTIVE,
        OSRS_CLIENT,
        ORTHOGRAPHIC,
        TOP_DOWN
    }

    private static final float MOVE_UNITS_PER_SECOND = 1400.0f;
    private static final float TURN_RADIANS_PER_SECOND = 2.4f;

    private CameraState camera;
    private ProjectionMode projectionMode = ProjectionMode.PERSPECTIVE;

    public ViewportController(CameraState initialCamera) {
        this(initialCamera, ProjectionMode.PERSPECTIVE);
    }

    public ViewportController(CameraState initialCamera, ProjectionMode initialMode) {
        this.camera = Objects.requireNonNull(initialCamera, "initial camera");
        this.projectionMode = Objects.requireNonNull(initialMode, "initial mode");
    }

    public CameraState camera() {
        return camera;
    }

    public void setCamera(CameraState next) {
        camera = Objects.requireNonNull(next, "camera");
    }

    public ProjectionMode projectionMode() {
        return projectionMode;
    }

    public void setProjectionMode(ProjectionMode mode) {
        this.projectionMode = Objects.requireNonNull(mode, "projectionMode");
    }

    /** Centers the camera to look down directly on the terrain. */
    public void top() {
        camera = new CameraState(camera.x(), camera.y(), camera.z(),
                (float) -Math.PI / 2.0f + 0.05f, 0.0f);
    }

    /** Aligns camera facing directly North (yaw = 0). */
    public void north() {
        camera = new CameraState(camera.x(), camera.y(), camera.z(), camera.pitch(), 0.0f);
    }

    /** Aligns camera facing directly South (yaw = PI). */
    public void south() {
        camera = new CameraState(camera.x(), camera.y(), camera.z(), camera.pitch(), (float) Math.PI);
    }

    /** Aligns camera facing directly East (yaw = PI / 2). */
    public void east() {
        camera = new CameraState(camera.x(), camera.y(), camera.z(), camera.pitch(), (float) (Math.PI / 2.0));
    }

    /** Aligns camera facing directly West (yaw = -PI / 2). */
    public void west() {
        camera = new CameraState(camera.x(), camera.y(), camera.z(), camera.pitch(), (float) (-Math.PI / 2.0));
    }

    /** Frames the given world coordinate in the center of the viewport. */
    public void frameSelection(float targetX, float targetY, float targetZ) {
        camera = new CameraState(targetX, targetY - 1600.0f, targetZ - 2400.0f,
                (float) -Math.toRadians(35.0), 0.0f);
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
     * Moves the camera vertically along the scene's Y axis.
     * In OSRS coordinates, negative Y is up (higher in the sky) and positive Y is down.
     *
     * @param distance world units to move vertically; negative moves up, positive moves down
     */
    public void moveVertical(float distance) {
        if (distance == 0.0f) return;
        camera = new CameraState(camera.x(), camera.y() + distance, camera.z(),
                camera.pitch(), camera.yaw());
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

    /** Dispatches a neutral key event for camera navigation. */
    public boolean handleKeyEvent(EditorKeyEvent event, float deltaSeconds) {
        if (event == null || !event.pressed()) return false;
        float move = MOVE_UNITS_PER_SECOND * deltaSeconds * (event.shift() ? 3.0f : 1.0f);
        float turn = TURN_RADIANS_PER_SECOND * deltaSeconds;
        String key = event.key();
        return switch (key) {
            case "Up", "ArrowUp" -> { moveForward(move); yield true; }
            case "Down", "ArrowDown" -> { moveForward(-move); yield true; }
            case "Left", "ArrowLeft" -> { rotateYaw(-turn); yield true; }
            case "Right", "ArrowRight" -> { rotateYaw(turn); yield true; }
            case "e", "E", "PageUp" -> { moveVertical(-move); yield true; }
            case "q", "Q", "PageDown" -> { moveVertical(move); yield true; }
            default -> false;
        };
    }

    /** Applies one frame of input; deltas are pixels and wheel is ImGui-style. */
    public void update(float deltaX, float deltaY,
                       boolean pan, boolean orbit, float wheel) {
        if (pan) {
            // Pan relative to camera orientation: deltaX moves along camera-right, deltaY along camera-up/forward
            float sinYaw = (float) Math.sin(camera.yaw());
            float cosYaw = (float) Math.cos(camera.yaw());
            float panRightX = cosYaw;
            float panRightZ = -sinYaw;
            float panForwardX = sinYaw;
            float panForwardZ = cosYaw;

            float shiftX = -deltaX * 8.0f * panRightX + deltaY * 8.0f * panForwardX;
            float shiftZ = -deltaX * 8.0f * panRightZ + deltaY * 8.0f * panForwardZ;
            camera = new CameraState(
                    camera.x() + shiftX,
                    camera.y(),
                    camera.z() + shiftZ,
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
            // Dolly forward along the camera's ground heading rather than pitching down into the terrain:
            moveForward(zoom);
        }
    }
}
