package com.rspsi.editor.render;

/** Renderer-neutral camera snapshot. Angles are expressed in radians. */
public record CameraState(float x, float y, float z, float pitch, float yaw) {
    public CameraState {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(pitch) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Camera values must be finite");
        }
    }
}
