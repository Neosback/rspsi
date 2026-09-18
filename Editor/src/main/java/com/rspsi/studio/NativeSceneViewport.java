package com.rspsi.studio;

import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.ViewportController;
import com.rspsi.editor.viewport.Viewport;
import com.rspsi.renderer.opengl.OpenGlSceneRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;

import java.util.Objects;

/** Owns the native scene renderer and presents its resolved FBO texture to ImGui. */
public final class NativeSceneViewport implements AutoCloseable, Viewport {
    private final OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
    private final GlFramebuffer framebuffer = new GlFramebuffer();
    private boolean initialized;
    private boolean closed;
    private final ViewportController navigation = new ViewportController(
            // OSRS world-Y points down; the camera sits at a negative height
            // above the terrain so negative terrain heights rise on screen.
            new CameraState(3200.0f, -2400.0f, -4200.0f,
                    (float) -Math.toRadians(28.0), 0.0f));

    public void initialize() {
        if (initialized) return;
        renderer.initialize();
        initialized = true;
    }

    public void setCamera(CameraState camera) {
        navigation.setCamera(Objects.requireNonNull(camera, "camera"));
    }

    public OpenGlSceneRenderer.Statistics statistics() {
        return renderer.statistics();
    }

    /** Picking is intentionally deferred until native scene coordinates are exposed. */
    @Override
    public java.util.Optional<com.rspsi.editor.model.TileCoordinate> tileAt(float x, float y) {
        return java.util.Optional.empty();
    }

    public void render(GpuUploadPlan plan, float availableWidth, float availableHeight, int samples) {
        render(plan, availableWidth, availableHeight, samples, RenderPresentation.neutral());
    }

    public void render(GpuUploadPlan plan, float availableWidth, float availableHeight, int samples,
                       RenderPresentation presentation) {
        ensureReady();
        int width = Math.max(1, Math.round(availableWidth));
        int height = Math.max(1, Math.round(availableHeight));
        framebuffer.resize(width, height, samples);
        framebuffer.bindForScene();
        renderer.setFramebufferStatus(framebuffer.framebufferStatus());
        renderer.draw(plan, navigation.camera(), width, height, presentation);
        framebuffer.resolve();
        ImGui.image(framebuffer.texture(), width, height, 0.0f, 1.0f, 1.0f, 0.0f);
        updateCameraFromInput();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        framebuffer.close();
        if (initialized) renderer.close();
        initialized = false;
    }

    private void ensureReady() {
        if (closed || !initialized) throw new IllegalStateException("Native scene viewport is not initialized");
    }

    /** Applies navigation only while the FBO image owns the mouse. */
    private void updateCameraFromInput() {
        if (!ImGui.isItemHovered()) return;
        float dx = ImGui.getIO().getMouseDeltaX();
        float dy = ImGui.getIO().getMouseDeltaY();
        if (ImGui.isMouseDragging(ImGuiMouseButton.Middle, 1.0f)) {
            navigation.update(dx, dy, true, false, ImGui.getIO().getMouseWheel());
        } else if (ImGui.isMouseDragging(ImGuiMouseButton.Right, 1.0f)) {
            navigation.update(dx, dy, false, true, ImGui.getIO().getMouseWheel());
        } else {
            navigation.update(0.0f, 0.0f, false, false, ImGui.getIO().getMouseWheel());
        }
    }
}
