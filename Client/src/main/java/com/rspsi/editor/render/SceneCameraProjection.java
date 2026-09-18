package com.rspsi.editor.render;

/** Perspective parameters shared by software and native scene backends. */
public record SceneCameraProjection(
        float verticalFieldOfView,
        float nearPlane,
        float farPlane
) {
    public SceneCameraProjection {
        if (!Float.isFinite(verticalFieldOfView) || verticalFieldOfView <= 0
                || verticalFieldOfView >= Math.PI) {
            throw new IllegalArgumentException("Vertical field of view must be between 0 and PI");
        }
        if (!Float.isFinite(nearPlane) || !Float.isFinite(farPlane)
                || nearPlane <= 0 || farPlane <= nearPlane) {
            throw new IllegalArgumentException("Invalid camera clipping planes");
        }
    }

    /** The compact perspective used by the reference editor renderer. */
    public static SceneCameraProjection editorDefault() {
        return new SceneCameraProjection((float) Math.toRadians(50.0), 1.0f, 200_000.0f);
    }
}
