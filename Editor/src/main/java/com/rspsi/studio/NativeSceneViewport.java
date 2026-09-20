package com.rspsi.studio;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.picker.DdaScenePicker;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.ViewportController;
import com.rspsi.editor.render.NavigationService;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.viewport.Viewport;
import com.rspsi.renderer.opengl.OpenGlSceneRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.Objects;

/** Owns the native scene renderer and presents its resolved FBO texture to ImGui. */
public final class NativeSceneViewport implements AutoCloseable, Viewport {
    private final OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
    private final GlFramebuffer framebuffer = new GlFramebuffer();
    private final DdaScenePicker picker = new DdaScenePicker();
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
    private final NavigationService navigationService = new NavigationService(navigation);

    public void initialize() {
        if (initialized) return;
        renderer.initialize();
        initialized = true;
    }

    public void setCamera(CameraState camera) {
        navigation.setCamera(Objects.requireNonNull(camera, "camera"));
    }

    public ViewportController navigation() {
        return navigation;
    }

    /** Shared jump/history service used by minimap, world map and search panels. */
    public NavigationService navigationService() {
        return navigationService;
    }

    /** Back-face culling mode for model geometry; see the renderer. */
    public void setCullMode(int mode) {
        renderer.setCullMode(mode);
    }

    public int cullMode() {
        return renderer.cullMode();
    }

    public OpenGlSceneRenderer.Statistics statistics() {
        return renderer.statistics();
    }

    @Override
    public java.util.Optional<com.rspsi.editor.model.WorldTile> tileAt(float x, float y) {
        return pickAt(x, y).map(PickResult::tile);
    }

    private Integer pickPlaneRestriction;

    /**
     * Restricts clicks/picks to one plane even while other planes are also visible ("show all
     * levels") - the user editing plane 0 should not be able to select a plane-1 tile just
     * because it happens to be rendered. Pass {@code null} to remove the restriction.
     */
    public void setPickPlaneRestriction(Integer plane) {
        this.pickPlaneRestriction = plane;
    }

    /**
     * DDA-picks the last rendered plan at a position in viewport-local
     * pixels. The plan, size, and camera used for the most recent frame are
     * retained so a click resolves against exactly what the user saw.
     */
    public java.util.Optional<PickResult> pickAt(float x, float y) {
        if (lastPlan == null || lastWidth <= 0 || lastHeight <= 0) return java.util.Optional.empty();
        return picker.pick(lastPlan, navigation.camera(), lastWidth, lastHeight, x, y,
                SceneCameraProjection.editorDefault(), pickPlaneRestriction);
    }

    /** The most recent pick, updated when the user clicks inside the viewport. */
    public java.util.Optional<PickResult> selection() {
        return java.util.Optional.ofNullable(selection);
    }

    public java.util.Optional<PickResult> lastPick() {
        return selection();
    }

    public void clearSelection() {
        selection = null;
    }

    public void setSelection(PickResult selection) {
        this.selection = selection;
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

    public float imageOriginX() { return imageOriginX; }
    public float imageOriginY() { return imageOriginY; }
    public int lastWidth() { return lastWidth; }
    public int lastHeight() { return lastHeight; }

    public ViewportOverlayDraw createOverlayDraw() {
        return new ViewportOverlayDraw(ImGui.getWindowDrawList(), imageOriginX, imageOriginY,
                lastWidth, lastHeight, navigation.camera());
    }

    private final java.util.Set<String> enabledOverlays = new java.util.HashSet<>();

    public boolean isOverlayEnabled(String id, boolean defaultValue) {
        if (enabledOverlays.contains(id)) return true;
        return defaultValue && !enabledOverlays.contains("!" + id);
    }

    public void setOverlayEnabled(String id, boolean enabled) {
        if (enabled) {
            enabledOverlays.remove("!" + id);
            enabledOverlays.add(id);
        } else {
            enabledOverlays.remove(id);
            enabledOverlays.add("!" + id);
        }
    }

    public void renderOverlays(com.rspsi.editor.tool.EditorTool activeTool,
                               com.rspsi.editor.plugin.EditorPluginLifecycleManager pluginLifecycle) {
        if (lastPlan == null || lastWidth <= 0 || lastHeight <= 0) return;
        ViewportOverlayDraw draw = createOverlayDraw();
        if (activeTool != null) {
            try {
                activeTool.renderOverlay(draw);
            } catch (Exception ignored) {
            }
        }
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var context = pluginLifecycle.host().context();
            var sceneSnapshot = context.scene().map(com.rspsi.editor.plugin.EditorSceneAccess::snapshot).orElse(null);
            for (var reg : pluginLifecycle.host().registry().overlayRegistrations()) {
                if (!isOverlayEnabled(reg.id(), reg.enabledByDefault())) continue;
                try {
                    var overlay = pluginLifecycle.host().registry().createOverlay(reg.id());
                    overlay.render(sceneSnapshot, draw);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Feeds real mouse input to the active {@link com.rspsi.editor.tool.EditorTool} (Single/
     * Multi Select, the Tile Painter brush, Height Sculptor, etc). Must be called right after
     * {@link #render}, before any other ImGui widget call, so {@code isItemHovered()} still
     * refers to the scene image. Without this, tools never receive pointerDown/Drag/Up and
     * anything built on them (selection, click-drag painting) silently does nothing.
     */
    public void dispatchToolInput(EditorToolController toolController) {
        if (toolController == null || !ImGui.isItemHovered()) return;
        // Middle/right-drag orbit/pan the camera; don't also feed those to the active tool.
        if (ImGui.isMouseDragging(ImGuiMouseButton.Middle, 1.0f)
                || ImGui.isMouseDragging(ImGuiMouseButton.Right, 1.0f)) {
            return;
        }

        float localX = ImGui.getIO().getMousePosX() - imageOriginX;
        float localY = ImGui.getIO().getMousePosY() - imageOriginY;
        var io = ImGui.getIO();
        PointerEvent event = new PointerEvent(localX, localY, PointerButton.PRIMARY,
                io.getKeyShift(), io.getKeyCtrl(), io.getKeyAlt());

        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            toolController.pointerDown(event);
        } else if (ImGui.isMouseDown(ImGuiMouseButton.Left) && ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f)) {
            toolController.pointerDrag(event);
        } else if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            toolController.pointerUp(event);
        }
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
        boolean shift = io.getKeyShift();
        boolean ctrl = io.getKeyCtrl();
        boolean alt = io.getKeyAlt();
        if (ImGui.isKeyDown(ImGuiKey.UpArrow)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowUp", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.DownArrow)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowDown", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.LeftArrow)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowLeft", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.RightArrow)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowRight", true, false, shift, ctrl, alt, false), seconds);
        }
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
