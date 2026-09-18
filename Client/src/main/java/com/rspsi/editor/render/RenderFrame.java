package com.rspsi.editor.render;

import java.util.Objects;

/**
 * Immutable inputs for one renderer frame.
 *
 * <p>The scene upload plan and camera are captured together so a backend
 * cannot accidentally draw a new scene with an old camera or consult mutable
 * settings while submitting commands. The client cycle is explicit to make
 * animated textures deterministic in the software oracle and reproducible in
 * native capture tests.</p>
 */
public record RenderFrame(
        GpuUploadPlan plan,
        RenderConfig config,
        CameraState camera,
        int clientCycle
) {
    public RenderFrame {
        plan = Objects.requireNonNull(plan, "upload plan");
        config = Objects.requireNonNull(config, "render config");
        camera = Objects.requireNonNull(camera, "camera");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
    }
}
