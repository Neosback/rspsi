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
                    Math.max(160.0f, camera.y() - zoom),
                    camera.z() + zoom, camera.pitch(), camera.yaw());
        }
    }
}
