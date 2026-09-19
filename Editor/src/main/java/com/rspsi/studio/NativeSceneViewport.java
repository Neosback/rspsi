package com.rspsi.studio;

import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuPlanPicker;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.ViewportController;
import com.rspsi.editor.viewport.Viewport;
import com.rspsi.renderer.opengl.OpenGlSceneRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.Objects;

/** Owns the native scene renderer and presents its resolved FBO texture to ImGui. */
public final class NativeSceneViewport implements AutoCloseable, Viewport {
    /** One OSRS tile is 128 world units, so this crosses ~12 tiles a second. */
    private static final float MOVE_UNITS_PER_SECOND = 1536.0f;
    private static final float TURN_RADIANS_PER_SECOND = 1.8f;

    private final OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
    private final GlFramebuffer framebuffer = new GlFramebuffer();
    private final GpuPlanPicker picker = new GpuPlanPicker();
    private GpuUploadPlan lastPlan;
    private int lastWidth;
    private int lastHeight;
    private float imageOriginX;
    private float imageOriginY;
    private PickResult selection;
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

    @Override
    public java.util.Optional<com.rspsi.editor.model.TileCoordinate> tileAt(float x, float y) {
        return pickAt(x, y).map(PickResult::tile);
    }

    /**
     * Ray-picks the last rendered plan at a position in viewport-local
     * pixels. The plan, size, and camera used for the most recent frame are
     * retained so a click resolves against exactly what the user saw.
     */
    public java.util.Optional<PickResult> pickAt(float x, float y) {
        if (lastPlan == null || lastWidth <= 0 || lastHeight <= 0) return java.util.Optional.empty();
        return picker.pick(lastPlan, navigation.camera(), lastWidth, lastHeight, x, y);
    }

    /** The most recent pick, updated when the user clicks inside the viewport. */
    public java.util.Optional<PickResult> selection() {
        return java.util.Optional.ofNullable(selection);
    }

    public void clearSelection() {
        selection = null;
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
        // Remember what this frame actually drew so a click can be ray-cast
        // against the same plan, size, and camera the user was looking at.
        lastPlan = plan;
        lastWidth = width;
        lastHeight = height;
        imageOriginX = ImGui.getCursorScreenPosX();
        imageOriginY = ImGui.getCursorScreenPosY();
        ImGui.image(framebuffer.texture(), width, height, 0.0f, 1.0f, 1.0f, 0.0f);
        updateSelectionFromInput();
        updateCameraFromInput();
        updateCameraFromKeyboard();
    }

    /**
     * A plain left click inside the scene image selects whatever is under
     * the cursor. Dragging is excluded so orbiting or panning never changes
     * the selection out from under the user.
     */
    private void updateSelectionFromInput() {
        if (!ImGui.isItemHovered()) return;
        if (!ImGui.isMouseClicked(ImGuiMouseButton.Left)) return;
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left, 2.0f)) return;
        float localX = ImGui.getIO().getMousePosX() - imageOriginX;
        float localY = ImGui.getIO().getMousePosY() - imageOriginY;
        pickAt(localX, localY).ifPresentOrElse(hit -> selection = hit, () -> selection = null);
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

    /**
     * Arrow-key navigation: up/down drive the camera along the direction it
     * is facing, left/right turn it in place. Turning is deliberately a yaw
     * change rather than a sideways slide, so holding left sweeps the view
     * around like a person looking about rather than strafing the map.
     *
     * <p>This is not gated on hover the way the mouse is - the keys should
     * work whenever the editor has focus and nothing is capturing text - but
     * it is skipped while a text field is active so typing a region number
     * does not also fly the camera.</p>
     */
    private void updateCameraFromKeyboard() {
        var io = ImGui.getIO();
        if (io.getWantTextInput() || io.getWantCaptureKeyboard()) return;
        float seconds = Math.min(0.1f, Math.max(0.0f, io.getDeltaTime()));
        if (seconds <= 0.0f) return;
        boolean fast = io.getKeyShift();
        float move = MOVE_UNITS_PER_SECOND * seconds * (fast ? 3.0f : 1.0f);
        float turn = TURN_RADIANS_PER_SECOND * seconds;
        if (ImGui.isKeyDown(ImGuiKey.UpArrow)) navigation.moveForward(move);
        if (ImGui.isKeyDown(ImGuiKey.DownArrow)) navigation.moveForward(-move);
        if (ImGui.isKeyDown(ImGuiKey.LeftArrow)) navigation.rotateYaw(-turn);
        if (ImGui.isKeyDown(ImGuiKey.RightArrow)) navigation.rotateYaw(turn);
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
