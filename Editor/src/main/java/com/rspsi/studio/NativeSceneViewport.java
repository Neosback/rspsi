package com.rspsi.studio;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.BackfacePolicy;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.picker.DdaScenePicker;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.PickerId;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.ViewportController;
import com.rspsi.editor.render.NavigationService;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.viewport.SurfaceHit;
import com.rspsi.editor.viewport.Viewport;
import com.rspsi.renderer.opengl.OpenGlSceneRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.Objects;

/** Owns the native scene renderer and presents its resolved FBO texture to ImGui. */
public final class NativeSceneViewport implements AutoCloseable, Viewport {
    private final OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
    private com.rspsi.editor.EditorSession highlightSession;
    private boolean hoverHighlight = true;
    private boolean imageHovered;
    private com.rspsi.editor.render.GpuHighlightIndex highlightIndex;
    private Object highlightSelectionKey;
    private int[] selectedCommands = new int[0];
    private int highlightTexture;
    private com.rspsi.editor.render.ScenePlaneFilter renderedPlaneFilter;
    private com.rspsi.editor.render.SceneHighlight renderedHighlight =
            com.rspsi.editor.render.SceneHighlight.NONE;
    private final GlFramebuffer framebuffer = new GlFramebuffer();
    private final DdaScenePicker picker = new DdaScenePicker();
    private GpuUploadPlan lastPlan;
    private GpuZonedUploadPlan zonedPlan;
    private CameraState lastFrameCamera;
    private SceneCameraProjection lastFrameProjection;
    private int lastWidth;
    private int lastHeight;
    private float imageOriginX;
    private float imageOriginY;
    private PickResult selection;
    // CPU DDA is the authoritative editor picker and does not require a second
    // scene render or synchronous GPU readback. GPU ID picking remains available
    // as an explicit diagnostic/acceleration option.
    private boolean gpuPickingEnabled = false;
    private GpuUploadPlan cachedPickPlan;
    private GpuZonedUploadPlan cachedPickZonedPlan;
    private CameraState cachedPickCamera;
    private SceneCameraProjection cachedPickProjection;
    private Integer cachedPickPlaneRestriction;
    private int cachedPickWidth;
    private int cachedPickHeight;
    private float cachedPickX = Float.NaN;
    private float cachedPickY = Float.NaN;
    private java.util.Optional<PickResult> cachedPickResult;
    private GpuUploadPlan renderedPlan;
    private GpuZonedUploadPlan renderedZonedPlan;
    private CameraState renderedCamera;
    private RenderPresentation renderedPresentation;
    private int renderedWidth;
    private int renderedHeight;
    private int renderedSamples = -1;
    private int renderedCullMode = -1;
    private int renderedTextureCycle = -1;
    private GpuUploadPlan animatedTexturePlan;
    private boolean animatedTexturePlanValue;
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
        renderer.setGpuPickingEnabled(gpuPickingEnabled);
        framebuffer.setCapabilityProfile(renderer.capabilityProfile());
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

    /** Planes drawn this frame; switching them never rebuilds or re-uploads geometry. */
    public void setPlaneFilter(com.rspsi.editor.render.ScenePlaneFilter filter) {
        renderer.setPlaneFilter(filter);
    }

    /** Back-face culling mode for model geometry; see the renderer. */
    public void setCullMode(int mode) {
        renderer.setCullMode(mode);
    }

    /** Applies the neutral validation mode without leaking OpenGL constants into Client. */
    public void setCullMode(BackfacePolicy.NativeCullingMode mode) {
        Objects.requireNonNull(mode, "mode");
        int nativeMode = switch (mode) {
            case TWO_SIDED -> OpenGlSceneRenderer.CULL_OFF;
            case CLIENT_FRONT -> BackfacePolicy.nativeWinding()
                    == BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE
                    ? OpenGlSceneRenderer.CULL_FRONT_CCW
                    : OpenGlSceneRenderer.CULL_FRONT_CW;
            case REVERSED_DEBUG -> BackfacePolicy.nativeWinding()
                    == BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE
                    ? OpenGlSceneRenderer.CULL_FRONT_CW
                    : OpenGlSceneRenderer.CULL_FRONT_CCW;
        };
        renderer.setCullMode(nativeMode);
    }

    public int cullMode() {
        return renderer.cullMode();
    }

    /**
     * Supplies the native-ready 8x8 zone geometry corresponding to the flat
     * compatibility plan. A mismatched fingerprint is ignored by the renderer.
     */
    private GpuUploadPlan pickPlan;
    private GpuZonedUploadPlan pickZonedPlan;
    private com.rspsi.editor.render.SceneHighlight positionsSource;
    private float[] hoveredPositions = new float[0];
    private float[] selectedPositions = new float[0];

    /**
     * The plan picks and outlines are resolved against. Studio updates it on loads, edits
     * and render-setting changes but not on animation frames: static geometry does not move
     * between frames, and re-indexing the picker for every animation plan cost 0.3-1.9 s per
     * refresh while the pointer moved.
     */
    public void setPickPlan(GpuUploadPlan plan, GpuZonedUploadPlan zoned) {
        pickPlan = plan;
        pickZonedPlan = zoned;
    }

    /** Session whose selection is outlined; null outlines nothing. */
    public void setHighlightSession(com.rspsi.editor.EditorSession session) {
        highlightSession = session;
    }

    /** Whether the tile or object under the pointer is outlined. */
    public void setHoverHighlight(boolean enabled) {
        hoverHighlight = enabled;
    }

    private com.rspsi.editor.render.SceneHighlight resolveHighlight(GpuUploadPlan plan) {
        if (pickPlan != null) plan = pickPlan;
        if (plan == null) return com.rspsi.editor.render.SceneHighlight.NONE;
        highlightPlan = plan;
        if (highlightIndex != null && highlightIndex.getPlan() != plan) {
            highlightIndex = null;
            highlightSelectionKey = null;
        }
        int[] hovered = hoveredCommands();
        int[] selected = selectedCommands();
        if (hovered.length == 0 && selected.length == 0) return com.rspsi.editor.render.SceneHighlight.NONE;
        return new com.rspsi.editor.render.SceneHighlight(hovered, selected);
    }

    /** What the pointer is over: a tile's terrain, or one placed location on its anchor. */
    private record HoverTarget(com.rspsi.editor.model.WorldTileAddress tile, boolean object,
                               com.rspsi.editor.render.SceneObjectIdentity identity, int objectId) {
    }

    private HoverTarget hoverTarget;
    private GpuUploadPlan highlightPlan;

    /** Built on first use per plan: idle frames with nothing hovered or selected skip it. */
    private com.rspsi.editor.render.GpuHighlightIndex highlightIndex() {
        if (highlightIndex == null || highlightIndex.getPlan() != highlightPlan) {
            highlightIndex = new com.rspsi.editor.render.GpuHighlightIndex(highlightPlan);
        }
        return highlightIndex;
    }
    private int hoverPickX = Integer.MIN_VALUE;
    private int hoverPickY = Integer.MIN_VALUE;
    private CameraState hoverPickCamera;
    private Integer hoverPickPlane;

    private int[] hoveredCommands() {
        if (!hoverHighlight || !imageHovered || ImGui.isMouseDragging(0, 1.0f)) {
            hoverTarget = null;
            hoverPickCamera = null;
            return new int[0];
        }
        int x = (int) (ImGui.getIO().getMousePosX() - imageOriginX);
        int y = (int) (ImGui.getIO().getMousePosY() - imageOriginY);
        // Pick only when the pointer, camera or pick plane moves. Animation swaps the plan
        // several times a second; re-picking then would rebuild the picker's spatial zones
        // on the render thread while the mouse rests. The target is re-resolved instead.
        CameraState camera = navigation.camera();
        if (x != hoverPickX || y != hoverPickY || !Objects.equals(camera, hoverPickCamera)
                || !Objects.equals(pickPlaneRestriction, hoverPickPlane)) {
            hoverTarget = pickAt(x, y).map(NativeSceneViewport::hoverTargetOf).orElse(null);
            hoverPickX = x;
            hoverPickY = y;
            hoverPickCamera = camera;
            hoverPickPlane = pickPlaneRestriction;
        }
        HoverTarget target = hoverTarget;
        if (target == null) return new int[0];
        if (!target.object()) return highlightIndex().terrain(target.tile());
        return target.identity().present()
                ? highlightIndex().location(target.tile(), target.identity())
                : highlightIndex().location(target.tile(), target.objectId());
    }

    private static HoverTarget hoverTargetOf(PickResult hit) {
        if (hit.objectHit() && (hit.sceneObjectIdentity().present() || hit.objectId() >= 0)) {
            WorldTile anchor = hit.objectTile() != null ? hit.objectTile() : hit.tile();
            return new HoverTarget(com.rspsi.editor.model.WorldTileAddress.of(
                    anchor.x(), anchor.y(), anchor.plane()), true,
                    hit.sceneObjectIdentity(), hit.objectId());
        }
        return new HoverTarget(com.rspsi.editor.model.WorldTileAddress.of(
                hit.tile().x(), hit.tile().y(), hit.tile().plane()), false,
                com.rspsi.editor.render.SceneObjectIdentity.none(), -1);
    }

    private int[] selectedCommands() {
        var session = highlightSession;
        if (session == null) return new int[0];
        var model = session.selection();
        var current = model.current();
        java.util.Set<com.rspsi.editor.model.TileCoordinate> tiles = model.selectedCoordinates();
        Object key = java.util.List.of(java.util.Objects.requireNonNullElse(current, ""), tiles);
        if (key.equals(highlightSelectionKey)) return selectedCommands;
        java.util.List<int[]> parts = new java.util.ArrayList<>();
        java.util.Set<com.rspsi.editor.model.WorldObject> objects = switch (current) {
            case com.rspsi.editor.selection.ObjectSelection single -> java.util.Set.of(single.object());
            case com.rspsi.editor.selection.ObjectSetSelection set -> set.objects();
            case null, default -> java.util.Set.of();
        };
        for (var object : objects) {
            var world = session.coordinates().toWorld(new com.rspsi.editor.model.LocalTile(
                    object.plane(), object.x(), object.y()));
            parts.add(highlightIndex().location(com.rspsi.editor.model.WorldTileAddress.of(
                    world.x(), world.y(), world.plane()), object.id(), object.type(), object.rotation()));
        }
        if (objects.isEmpty()) {
            for (var tile : tiles) {
                var world = session.coordinates().toWorld(new com.rspsi.editor.model.LocalTile(
                        tile.plane(), tile.x(), tile.y()));
                parts.add(highlightIndex().terrain(com.rspsi.editor.model.WorldTileAddress.of(
                        world.x(), world.y(), world.plane())));
            }
        }
        selectedCommands = parts.stream().flatMapToInt(java.util.Arrays::stream).toArray();
        highlightSelectionKey = key;
        return selectedCommands;
    }

    public void setZonedPlan(GpuZonedUploadPlan plan) {
        zonedPlan = plan;
    }

    /**
     * Marks the editable region; neighbouring context tiles draw dimmed. Pass
     * {@code null} to draw everything at full brightness.
     */
    public void setEditableRegion(com.rspsi.editor.model.WorldRegion region) {
        if (region == null) {
            renderer.setEditBounds(null);
            return;
        }
        float minX = region.regionX() * com.rspsi.editor.model.WorldRegion.REGION_SIZE * 128f;
        float minZ = region.regionY() * com.rspsi.editor.model.WorldRegion.REGION_SIZE * 128f;
        float size = com.rspsi.editor.model.WorldRegion.REGION_SIZE * 128f;
        renderer.setEditBounds(new com.rspsi.editor.render.SceneFog.Bounds(
                minX, minX + size, minZ, minZ + size));
    }

    public OpenGlSceneRenderer.Statistics statistics() {
        return renderer.statistics();
    }

    public com.rspsi.renderer.opengl.OpenGlCapabilityProfile capabilityProfile() {
        return renderer.capabilityProfile();
    }

    @Override
    public java.util.Optional<com.rspsi.editor.model.WorldTile> tileAt(float x, float y) {
        return hitAt(x, y).map(SurfaceHit::targetTile);
    }

    /**
     * Resolves the specific {@link com.rspsi.editor.model.WorldObject} a ray-picked
     * tile/id pair refers to. This viewport only speaks {@link PickResult} (a tile plus
     * a raw cache object id); it holds no {@code EditorSession}/world document to turn
     * that into a real placed object (type, rotation), so Studio wires this in once it
     * has one. Left unset, {@link #objectAt} degrades to {@code Optional.empty()} - the
     * same "legacy viewport" fallback {@link Viewport#objectAt} already documents.
     */
    @FunctionalInterface
    public interface WorldObjectResolver {
        java.util.Optional<com.rspsi.editor.model.WorldObject> resolve(WorldTile objectTile, int objectId);
    }

    private WorldObjectResolver objectResolver;

    public void setObjectResolver(WorldObjectResolver resolver) {
        this.objectResolver = resolver;
    }

    /**
     * Precise "what object is actually under the cursor" pick, in the spirit of
     * RuneLite's clickbox hit test - resolves to the one specific object the ray hit,
     * not every object present on that tile. {@link #tileAt} still exists for
     * tile-target tools; this is the object-target counterpart {@link BoxSelectTool}
     * needs so a single click on a tile stacked with a wall, a wall decoration, and a
     * ground object doesn't select all three at once.
     */
    @Override
    public java.util.Optional<com.rspsi.editor.model.WorldObject> objectAt(float x, float y) {
        return hitAt(x, y).flatMap(SurfaceHit::object);
    }

    /**
     * Projects the renderer's rich PickResult into the stable semantic editor
     * hit contract. Submission priority, depth bias, texture ids and other
     * renderer bookkeeping intentionally stop at this boundary.
     */
    @Override
    public java.util.Optional<SurfaceHit> hitAt(float x, float y) {
        return pickAt(x, y).map(this::semanticHit);
    }

    /**
     * Package-visible for native contract tests. This is the only conversion
     * point where renderer picking metadata becomes public editor semantics.
     */
    SurfaceHit semanticHit(PickResult hit) {
        Objects.requireNonNull(hit, "hit");
        if (!hit.objectHit()) {
            return SurfaceHit.terrain(hit.tile());
        }

        WorldTile anchor = hit.objectTile() != null ? hit.objectTile() : hit.tile();
        com.rspsi.editor.model.WorldObject placement = objectResolver == null
                ? null
                : objectResolver.resolve(anchor, hit.objectId()).orElse(null);
        return SurfaceHit.object(hit.tile(), hit.objectId(), anchor, placement);
    }

    private Integer pickPlaneRestriction;

    /**
     * Restricts clicks/picks to one plane even while other planes are also visible ("show all
     * levels") - the user editing plane 0 should not be able to select a plane-1 tile just
     * because it happens to be rendered. Pass {@code null} to remove the restriction.
     */
    public void setPickPlaneRestriction(Integer plane) {
        if (Objects.equals(this.pickPlaneRestriction, plane)) return;
        this.pickPlaneRestriction = plane;
        invalidatePickCache();
    }

    /**
     * DDA-picks the last rendered plan at a position in viewport-local
     * pixels. The plan, size, and camera used for the most recent frame are
     * retained so a click resolves against exactly what the user saw.
     */
    public java.util.Optional<PickResult> pickAt(float x, float y) {
        GpuUploadPlan plan = pickPlan != null ? pickPlan : lastPlan;
        GpuZonedUploadPlan zoned = pickPlan != null ? pickZonedPlan : zonedPlan;
        if (plan == null || lastFrameCamera == null || lastFrameProjection == null
                || lastWidth <= 0 || lastHeight <= 0) {
            return java.util.Optional.empty();
        }

        if (samePickQuery(x, y)) {
            return cachedPickResult;
        }

        java.util.Optional<PickResult> result = java.util.Optional.empty();
        if (gpuPickingEnabled && plan == lastPlan) {
            int packedId = renderer.pickId(
                    lastPlan, zonedPlan, lastFrameCamera, lastWidth, lastHeight,
                    x, y, pickPlaneRestriction);
            if (PickerId.isValid(packedId)) {
                result = picker.pickMatchingId(
                        lastPlan, zonedPlan, lastFrameCamera, lastWidth, lastHeight,
                        x, y, lastFrameProjection, pickPlaneRestriction, packedId);
            }
        }

        if (result.isEmpty()) {
            // The DDA path remains the authoritative fallback/reference. This also
            // protects selection if a driver rejects the optional integer pass or
            // a future shader/visibility change temporarily breaks GPU parity.
            result = picker.pick(plan, zoned, lastFrameCamera, lastWidth, lastHeight, x, y,
                    lastFrameProjection, pickPlaneRestriction);
        }

        cachePick(x, y, result);
        return result;
    }

    private boolean samePickQuery(float x, float y) {
        return cachedPickResult != null
                && cachedPickPlan == (pickPlan != null ? pickPlan : lastPlan)
                && cachedPickZonedPlan == zonedPlan
                && Objects.equals(cachedPickCamera, lastFrameCamera)
                && Objects.equals(cachedPickProjection, lastFrameProjection)
                && Objects.equals(cachedPickPlaneRestriction, pickPlaneRestriction)
                && cachedPickWidth == lastWidth
                && cachedPickHeight == lastHeight
                && (int) cachedPickX == (int) x
                && (int) cachedPickY == (int) y;
    }

    private void cachePick(float x, float y, java.util.Optional<PickResult> result) {
        cachedPickPlan = pickPlan != null ? pickPlan : lastPlan;
        cachedPickZonedPlan = zonedPlan;
        cachedPickCamera = lastFrameCamera;
        cachedPickProjection = lastFrameProjection;
        cachedPickPlaneRestriction = pickPlaneRestriction;
        cachedPickWidth = lastWidth;
        cachedPickHeight = lastHeight;
        cachedPickX = x;
        cachedPickY = y;
        cachedPickResult = Objects.requireNonNull(result, "pick result");
    }

    private void invalidatePickCache() {
        cachedPickPlan = null;
        cachedPickZonedPlan = null;
        cachedPickCamera = null;
        cachedPickProjection = null;
        cachedPickPlaneRestriction = null;
        cachedPickWidth = 0;
        cachedPickHeight = 0;
        cachedPickX = Float.NaN;
        cachedPickY = Float.NaN;
        cachedPickResult = null;
    }

    public void setGpuPickingEnabled(boolean enabled) {
        if (gpuPickingEnabled == enabled) return;
        gpuPickingEnabled = enabled;
        invalidatePickCache();
        // Auxiliary picker stream allocation is reconciled inside renderer.draw().
        // Force one redraw so enabling/disabling the optional path immediately
        // updates resident GPU resources even when the visible scene is static.
        renderedPlan = null;
        if (initialized) {
            renderer.setGpuPickingEnabled(enabled);
        }
    }

    public boolean gpuPickingEnabled() {
        return gpuPickingEnabled;
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
        renderer.setFramebufferStatus(framebuffer.framebufferStatus());

        // Freeze the complete camera/projection state for the frame before any
        // input is consumed. The scene image, picking and every world-space
        // overlay must resolve against this same snapshot. Navigation input
        // below intentionally updates only the next frame.
        CameraState frameCamera = navigation.camera();
        SceneCameraProjection frameProjection = SceneCameraProjection.editorDefault();
        int textureCycle = textureAnimationCycle();
        boolean animatedTextures = hasAnimatedTextures(plan);
        com.rspsi.editor.render.SceneHighlight highlight = resolveHighlight(plan);
        boolean redrawScene = renderedPlan != plan
                || renderedZonedPlan != zonedPlan
                || !Objects.equals(renderedCamera, frameCamera)
                || !Objects.equals(renderedPresentation, presentation)
                || renderedWidth != width
                || renderedHeight != height
                || renderedSamples != framebuffer.samples()
                || renderedCullMode != renderer.cullMode()
                || !renderer.planeFilter().equals(renderedPlaneFilter)
                || (animatedTextures && renderedTextureCycle != textureCycle);
        if (redrawScene) {
            framebuffer.bindForScene();
            renderer.draw(plan, zonedPlan, frameCamera, width, height, presentation);
            framebuffer.resolve();
            renderedPlan = plan;
            renderedZonedPlan = zonedPlan;
            renderedCamera = frameCamera;
            renderedPresentation = presentation;
            renderedWidth = width;
            renderedHeight = height;
            renderedSamples = framebuffer.samples();
            renderedCullMode = renderer.cullMode();
            renderedPlaneFilter = renderer.planeFilter();
            renderedTextureCycle = textureCycle;
        }
        // Outlines live in their own overlay texture: a hover change redraws a few triangles
        // and one full-screen pass, never the scene.
        if (redrawScene || !renderedHighlight.equals(highlight)) {
            if (!highlight.equals(positionsSource)) {
                hoveredPositions = highlight.isEmpty() ? new float[0] : highlightIndex().positions(highlight.getHovered());
                selectedPositions = highlight.isEmpty() ? new float[0] : highlightIndex().positions(highlight.getSelected());
                positionsSource = highlight;
            }
            highlightTexture = renderer.drawHighlightOverlay(hoveredPositions, selectedPositions, width, height);
            renderedHighlight = highlight;
        }
        recordPresentedFrame(plan, frameCamera, frameProjection, width, height);
        ImGui.image(framebuffer.texture(), width, height, 0.0f, 1.0f, 1.0f, 0.0f);
        imageOriginX = ImGui.getItemRectMinX();
        imageOriginY = ImGui.getItemRectMinY();
        if (highlightTexture != 0) {
            ImGui.getWindowDrawList().addImage(highlightTexture, imageOriginX, imageOriginY,
                    imageOriginX + width, imageOriginY + height, 0.0f, 1.0f, 1.0f, 0.0f);
        }
        imageHovered = ImGui.isItemHovered();
        updateSelectionFromInput();
        updateCameraFromInput();
        updateCameraFromKeyboard();
    }

    private boolean hasAnimatedTextures(GpuUploadPlan plan) {
        if (animatedTexturePlan != plan) {
            animatedTexturePlan = plan;
            animatedTexturePlanValue = plan.textures().values().stream()
                    .anyMatch(texture -> texture.definition().animationDirection() != 0
                            && texture.definition().animationSpeed() != 0);
        }
        return animatedTexturePlanValue;
    }

    private static int textureAnimationCycle() {
        long cycle = (System.nanoTime() / 1_000_000L) / 20L;
        return (int) (cycle & com.rspsi.editor.render.TextureAnimation.CLIENT_CYCLE_MASK);
    }

    public float imageOriginX() { return imageOriginX; }
    public float imageOriginY() { return imageOriginY; }
    public int lastWidth() { return lastWidth; }
    public int lastHeight() { return lastHeight; }

    /**
     * Camera snapshot that produced the currently presented scene image.
     * Navigation may already contain input for the next frame, so overlays,
     * picking and diagnostics must use this value instead of navigation.camera().
     */
    public CameraState lastFrameCamera() { return lastFrameCamera; }

    /** Projection paired with {@link #lastFrameCamera()} for the presented frame. */
    public SceneCameraProjection lastFrameProjection() { return lastFrameProjection; }

    void recordPresentedFrame(GpuUploadPlan plan, CameraState camera,
                              SceneCameraProjection projection, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Presented frame dimensions must be positive");
        }
        lastPlan = plan;
        lastFrameCamera = Objects.requireNonNull(camera, "camera");
        lastFrameProjection = Objects.requireNonNull(projection, "projection");
        lastWidth = width;
        lastHeight = height;
    }

    /** The exact plan submitted on the most recent frame, for inspectors that want to show
     * which real {@code GpuDrawCommand}(s) a tile/object resolved to (texture, pass, priority,
     * depth bias) - e.g. to debug z-fighting or a coplanar-face ordering bug. */
    public GpuUploadPlan lastPlan() { return lastPlan; }

    private ViewportOverlayDraw.ViewportElevationSampler elevationSampler;

    public void setElevationSampler(ViewportOverlayDraw.ViewportElevationSampler sampler) {
        this.elevationSampler = sampler;
    }

    public ViewportOverlayDraw createOverlayDraw() {
        return createOverlayDraw(elevationSampler);
    }

    public ViewportOverlayDraw createOverlayDraw(ViewportOverlayDraw.ViewportElevationSampler sampler) {
        if (lastFrameCamera == null || lastFrameProjection == null) {
            throw new IllegalStateException("No presented scene frame is available for overlay projection");
        }
        return new ViewportOverlayDraw(ImGui.getWindowDrawList(), imageOriginX, imageOriginY,
                lastWidth, lastHeight, lastFrameCamera, lastFrameProjection, sampler);
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
        if (lastPlan == null || lastFrameCamera == null || lastFrameProjection == null
                || lastWidth <= 0 || lastHeight <= 0) return;
        ViewportOverlayDraw.ViewportElevationSampler sampler = this.elevationSampler;
        if (sampler == null && pluginLifecycle != null && pluginLifecycle.host() != null) {
            var session = pluginLifecycle.host().context().session();
            if (session != null) {
                sampler = (plane, x, y) -> {
                    var local = session.coordinates().toLocal(new WorldTile(plane, x, y)).orElse(null);
                    if (local == null || !session.world().contains(local)) return null;
                    var snap = session.world().tile(local).snapshot();
                    return new float[]{snap.southWestHeight(), snap.southEastHeight(),
                            snap.northEastHeight(), snap.northWestHeight()};
                };
                this.elevationSampler = sampler;
            }
        }
        ViewportOverlayDraw draw = createOverlayDraw(sampler);
        if (activeTool != null) {
            try {
                activeTool.renderOverlay(draw);
            } catch (Exception ignored) {
            }
        }
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var context = pluginLifecycle.host().context();
            com.rspsi.editor.plugin.EditorSceneSnapshot sceneSnapshot = null;
            boolean snapshotResolved = false;
            for (var reg : pluginLifecycle.host().registry().overlayRegistrations()) {
                if (!isOverlayEnabled(reg.id(), reg.enabledByDefault())) continue;
                if (!snapshotResolved) {
                    sceneSnapshot = context.scene()
                            .map(com.rspsi.editor.plugin.EditorSceneAccess::snapshot).orElse(null);
                    snapshotResolved = true;
                }
                try {
                    var overlay = pluginLifecycle.host().registry().createOverlay(reg.id());
                    overlay.render(sceneSnapshot, draw);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean toolInteracting = false;

    /**
     * Feeds real mouse input to the active {@link com.rspsi.editor.tool.EditorTool} (Single/
     * Multi Select, the Tile Painter brush, Height Sculptor, etc). Must be called right after
     * {@link #render}, before any other ImGui widget call, so {@code isItemHovered()} still
     * refers to the scene image. Without this, tools never receive pointerDown/Drag/Up and
     * anything built on them (selection, click-drag painting) silently does nothing.
     */
    public void dispatchToolInput(EditorToolController toolController) {
        if (toolController == null) return;
        boolean hovered = ImGui.isItemHovered();

        if (toolInteracting) {
            if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                toolInteracting = false;
                float localX = ImGui.getIO().getMousePosX() - imageOriginX;
                float localY = ImGui.getIO().getMousePosY() - imageOriginY;
                var io = ImGui.getIO();
                PointerEvent event = new PointerEvent(localX, localY, PointerButton.PRIMARY,
                        io.getKeyShift(), io.getKeyCtrl(), io.getKeyAlt());
                toolController.pointerUp(event);
                return;
            }
        }

        if (!hovered) return;
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
            toolInteracting = true;
            toolController.pointerDown(event);
        } else if (ImGui.isMouseDown(ImGuiMouseButton.Left) && ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f)) {
            toolController.pointerDrag(event);
        } else if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            toolInteracting = false;
            toolController.pointerUp(event);
        } else if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            PointerEvent rightEvent = new PointerEvent(localX, localY, PointerButton.SECONDARY,
                    io.getKeyShift(), io.getKeyCtrl(), io.getKeyAlt());
            toolController.pointerDown(rightEvent);
        } else {
            toolController.pointerMove(event);
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
        if (io.getWantTextInput()) return;
        float seconds = Math.min(0.1f, Math.max(0.0f, io.getDeltaTime()));
        if (seconds <= 0.0f) return;
        boolean shift = io.getKeyShift();
        boolean ctrl = io.getKeyCtrl();
        boolean alt = io.getKeyAlt();
        if (ImGui.isKeyDown(ImGuiKey.UpArrow) || ImGui.isKeyDown(ImGuiKey.W)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowUp", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.DownArrow) || ImGui.isKeyDown(ImGuiKey.S)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowDown", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.LeftArrow) || ImGui.isKeyDown(ImGuiKey.A)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowLeft", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.RightArrow) || ImGui.isKeyDown(ImGuiKey.D)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("ArrowRight", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.E) || ImGui.isKeyDown(ImGuiKey.PageUp)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("E", true, false, shift, ctrl, alt, false), seconds);
        }
        if (ImGui.isKeyDown(ImGuiKey.Q) || ImGui.isKeyDown(ImGuiKey.PageDown)) {
            navigation.handleKeyEvent(new com.rspsi.editor.input.EditorKeyEvent("Q", true, false, shift, ctrl, alt, false), seconds);
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
