package com.rspsi.ui.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SelectionChangeListener;
import com.rspsi.editor.collision.CollisionDirection;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.RoutePreview;
import com.rspsi.editor.collision.RoutePreviewMode;
import com.rspsi.editor.collision.RoutePreviewService;
import com.rspsi.editor.debug.DebugGridLevel;
import com.rspsi.editor.debug.DebugOverlayMode;
import com.rspsi.editor.debug.DebugOverlaySettings;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.io.WorldFragmentCodec;
import com.rspsi.editor.PasteFragmentCommand;
import com.rspsi.editor.selection.FragmentSelection;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderChanges;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.SceneRenderer;
import com.rspsi.editor.render.SessionSceneController;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainVertex;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.viewport.Viewport;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Small JavaFX adapter for the canonical scene model.
 *
 * <p>This is a top-down semantic preview, not the replacement 3D renderer.
 * It gives the controlled OSRS project workflow a real neutral scene surface
 * while the faithful legacy/GPU renderer remains a separately gated task.</p>
 */
public final class CanonicalSceneViewport extends StackPane implements SceneRenderer, Viewport, AutoCloseable {
    private static final double TILE_PIXELS = 10.0;

    private final Canvas canvas = new Canvas();
    private final EditorToolController toolController = new EditorToolController();
    private final SelectionChangeListener selectionListener = ignored -> redrawOnFxThread();
    private EditorSession session;
    private WorldWindow worldWindow;
    private RenderScene scene;
    private SessionSceneController sceneController;
    private int plane;
    private DebugOverlaySettings debugOverlaySettings = DebugOverlaySettings.none();
    private RoutePreview routePreview;
    private Consumer<Optional<TileCoordinate>> hoverListener = ignored -> { };
    private AssetRepository assets = EmptyAssetRepository.INSTANCE;
    private EditorInputRouter inputRouter;
    private boolean closed;

    public CanonicalSceneViewport() {
        getStyleClass().add("canonical-scene-viewport");
        setAccessibleText("Canonical OSRS scene preview");
        setFocusTraversable(true);
        canvas.setOnMousePressed(this::toolPointerDown);
        canvas.setOnMouseDragged(this::toolPointerDrag);
        canvas.setOnMouseReleased(this::toolPointerUp);
        canvas.setOnMouseClicked(event -> {
            if (toolController.activeTool() == null) pickAndSelect(event.getX(), event.getY());
        });
        canvas.setOnMouseMoved(event -> notifyHover(event.getX(), event.getY()));
        canvas.setOnMouseExited(event -> hoverListener.accept(Optional.empty()));
        getChildren().add(canvas);
    }

    public void bind(EditorSession session, WorldWindow worldWindow) {
        bind(session, worldWindow, null);
    }

    /** Binds a canonical scene with optional neutral definitions for footprints and materials. */
    public void bind(EditorSession session, WorldWindow worldWindow,
                     DefinitionProvider definitions) {
        bind(session, worldWindow, definitions, null);
    }

    /** Binds the canonical scene and the optional neutral asset source used by tools. */
    public void bind(EditorSession session, WorldWindow worldWindow,
                     DefinitionProvider definitions, AssetRepository assets) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(worldWindow, "worldWindow");
        closeBinding();
        this.session = session;
        this.worldWindow = worldWindow;
        this.assets = assets == null ? EmptyAssetRepository.INSTANCE : assets;
        session.selection().addChangeListener(selectionListener);
        closed = false;
        scene = null;
        sceneController = definitions == null
                ? new SessionSceneController(session, this)
                : new SessionSceneController(session, this, new RenderSceneBuilder(definitions));
        redrawOnFxThread();
    }

    public EditorTool activeTool() {
        return toolController.activeTool();
    }

    /** Activates a neutral editor tool for canonical viewport pointer input. */
    public void activateTool(EditorTool tool) {
        if (session == null) throw new IllegalStateException("Viewport is not bound to a session");
        toolController.activate(tool, new ToolContext(session, assets, this));
        redrawOnFxThread();
    }

    public void deactivateTool() {
        toolController.deactivate();
        redrawOnFxThread();
    }

    /** Binds the shared shortcut/pointer router to the active plugin host. */
    public void bindPluginHost(EditorPluginHost host) {
        Objects.requireNonNull(host, "host");
        inputRouter = new EditorInputRouter(host.context(), toolController);
    }

    /** Clears frontend input callbacks before the plugin host is unloaded. */
    public void clearPluginHost() {
        inputRouter = null;
    }

    /** Dispatches one frontend-translated key event through plugin shortcuts. */
    public boolean dispatchKey(EditorKeyEvent event, boolean textInputFocused) {
        return inputRouter != null && inputRouter.key(event, textInputFocused);
    }

    public EditorSession session() {
        return session;
    }

    public WorldWindow worldWindow() {
        return worldWindow;
    }

    /** Returns the host-owned derived scene for neutral plugin consumers. */
    public RenderScene sceneSnapshot() {
        return scene;
    }

    /** Returns the immutable scene view used by neutral plugin consumers. */
    public EditorSceneSnapshot sceneSnapshotView() {
        return EditorSceneSnapshot.from(scene);
    }

    public int plane() {
        return plane;
    }

    /** Returns the neutral debug modes currently rendered by this frontend. */
    public DebugOverlaySettings debugOverlaySettings() {
        return debugOverlaySettings;
    }

    /** Updates frontend rendering only; semantic overlay data remains neutral. */
    public void setDebugOverlaySettings(DebugOverlaySettings settings) {
        this.debugOverlaySettings = Objects.requireNonNull(settings, "settings");
        redrawOnFxThread();
    }

    /** Returns the latest neutral route/LOS/reach result shown by this viewport. */
    public Optional<RoutePreview> routePreview() {
        return Optional.ofNullable(routePreview);
    }

    /** Computes a bounded collision preview in local scene coordinates. */
    public RoutePreview previewRoute(RoutePreviewMode mode, int startX, int startY,
                                     int targetX, int targetY, int actorSize,
                                     boolean useRouteBlockers) {
        if (scene == null) throw new IllegalStateException("Viewport is not bound to a scene");
        TileCoordinate start = new TileCoordinate(plane, startX, startY);
        TileCoordinate target = new TileCoordinate(plane, targetX, targetY);
        RoutePreview preview;
        if (mode == RoutePreviewMode.REACH && session != null
                && session.selection().current() instanceof ObjectSelection selection) {
            WorldObject object = selection.object();
            int width = 1;
            int length = 1;
            for (var renderObject : scene.renderObjects()) {
                if (renderObject.object().equals(object)) {
                    width = renderObject.footprintWidth();
                    length = renderObject.footprintLength();
                    target = new TileCoordinate(plane, object.x(), object.y());
                    break;
                }
            }
            preview = RoutePreviewService.evaluate(collisionMap(), mode, start, target,
                    width, length, 4096, actorSize, useRouteBlockers);
        } else {
            preview = RoutePreviewService.evaluate(collisionMap(), mode, start, target,
                    4096, actorSize, useRouteBlockers);
        }
        routePreview = preview;
        redrawOnFxThread();
        return preview;
    }

    public void clearRoutePreview() {
        routePreview = null;
        redrawOnFxThread();
    }

    /** Copies the selected world rectangle as versioned neutral JSON. */
    public String copySelectionToClipboard() {
        if (session == null) return "No editor session";
        WorldFragment fragment = selectedFragment();
        if (fragment == null) return "Select tiles or objects first";
        ClipboardContent content = new ClipboardContent();
        content.putString(WorldFragmentCodec.encode(fragment));
        Clipboard.getSystemClipboard().setContent(content);
        session.selection().selectFragment(fragment);
        return "Copied fragment " + fragment.bounds().width() + " × " + fragment.bounds().height();
    }

    /** Pastes the clipboard fragment through one undoable canonical command. */
    public String pasteFragmentFromClipboard(int targetX, int targetY) {
        if (session == null) return "No editor session";
        if (!Clipboard.getSystemClipboard().hasString()) return "Clipboard has no world fragment";
        try {
            WorldFragment fragment = WorldFragmentCodec.decode(
                    Clipboard.getSystemClipboard().getString());
            session.execute(new PasteFragmentCommand(fragment, targetX, targetY));
            return "Pasted fragment " + fragment.bounds().width() + " × " + fragment.bounds().height();
        } catch (RuntimeException exception) {
            return "Paste failed: " + exception.getMessage();
        }
    }

    /** Writes the selected fragment using the neutral versioned interchange format. */
    public String exportSelection(Path file) {
        Objects.requireNonNull(file, "file");
        if (session == null) return "No editor session";
        WorldFragment fragment = selectedFragment();
        if (fragment == null) return "Select tiles or objects first";
        try {
            Files.writeString(file, WorldFragmentCodec.encode(fragment));
            return "Exported fragment " + fragment.bounds().width() + " × "
                    + fragment.bounds().height();
        } catch (IOException exception) {
            return "Export failed: " + exception.getMessage();
        }
    }

    /** Reads a neutral fragment and applies it through one undoable command. */
    public String importFragment(Path file, int targetX, int targetY) {
        Objects.requireNonNull(file, "file");
        if (session == null) return "No editor session";
        try {
            WorldFragment fragment = WorldFragmentCodec.decode(Files.readString(file));
            session.execute(new PasteFragmentCommand(fragment, targetX, targetY,
                    "Import world fragment"));
            return "Imported fragment " + fragment.bounds().width() + " × "
                    + fragment.bounds().height();
        } catch (IOException | RuntimeException exception) {
            return "Import failed: " + exception.getMessage();
        }
    }

    private WorldFragment selectedFragment() {
        Selection selection = session.selection().current();
        if (selection instanceof FragmentSelection fragment) return fragment.fragment();
        TileBounds bounds = null;
        if (selection instanceof TileSelection tile) {
            bounds = new TileBounds(tile.coordinate().x(), tile.coordinate().y(),
                    tile.coordinate().x(), tile.coordinate().y());
        } else if (selection instanceof TileAreaSelection area) {
            bounds = area.bounds();
        } else if (selection instanceof TileSetSelection tiles) {
            bounds = boundsOf(tiles.coordinates());
        } else if (selection instanceof ObjectSelection object) {
            bounds = new TileBounds(object.object().x(), object.object().y(),
                    object.object().x(), object.object().y());
        } else if (selection instanceof ObjectSetSelection objects) {
            bounds = boundsOf(objects.objects().stream()
                    .map(object -> new TileCoordinate(object.plane(), object.x(), object.y())).toList());
        }
        return bounds == null ? null : WorldFragment.capture(session.world(), bounds);
    }

    private static TileBounds boundsOf(java.util.Collection<TileCoordinate> tiles) {
        if (tiles == null || tiles.isEmpty()) return null;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TileCoordinate tile : tiles) {
            minX = Math.min(minX, tile.x());
            minY = Math.min(minY, tile.y());
            maxX = Math.max(maxX, tile.x());
            maxY = Math.max(maxY, tile.y());
        }
        return new TileBounds(minX, minY, maxX, maxY);
    }

    /** Installs a frontend callback for hover inspection without changing selection. */
    public void setHoverListener(Consumer<Optional<TileCoordinate>> listener) {
        this.hoverListener = Objects.requireNonNull(listener, "listener");
    }

    public void setPlane(int plane) {
        if (session != null && (plane < 0 || plane >= session.world().planes())) {
            throw new IllegalArgumentException("Plane outside session: " + plane);
        }
        this.plane = plane;
        redrawOnFxThread();
    }

    @Override
    public void load(RenderScene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
        if (plane >= scene.document().planes()) plane = 0;
        redrawOnFxThread();
    }

    @Override
    public void update(RenderChanges changes) {
        redrawOnFxThread();
    }

    @Override
    public void update(RenderScene scene, RenderChanges changes) {
        load(scene);
    }

    @Override
    public void render(CameraState camera) {
        redrawOnFxThread();
    }

    @Override
    public Optional<PickResult> pick(float x, float y) {
        if (scene == null || x < 0 || y < 0) return Optional.empty();
        int tileX = (int) (x / TILE_PIXELS);
        int tileY = (int) (y / TILE_PIXELS);
        if (tileX >= scene.document().width() || tileY >= scene.document().length()) {
            return Optional.empty();
        }
        TileCoordinate tile = new TileCoordinate(plane, tileX, tileY);
        return Optional.of(new PickResult(tile, plane));
    }

    @Override
    public Optional<TileCoordinate> tileAt(float x, float y) {
        return pick(x, y).map(PickResult::tile);
    }

    @Override
    public Optional<WorldObject> objectAt(float x, float y) {
        if (scene == null) return Optional.empty();
        return tileAt(x, y).flatMap(tile -> scene.renderObjects().stream()
                .filter(renderObject -> renderObject.object().plane() == tile.plane())
                .filter(renderObject -> contains(renderObject, tile))
                .map(com.rspsi.editor.render.RenderObject::object)
                .findFirst());
    }

    private void pickAndSelect(double x, double y) {
        if (session == null) return;
        objectAt((float) x, (float) y).ifPresentOrElse(object -> {
            session.selection().selectObject(object);
            requestFocus();
        }, () -> pick((float) x, (float) y).ifPresent(result -> {
            session.selection().select(result.tile());
            requestFocus();
        }));
    }

    private static boolean contains(com.rspsi.editor.render.RenderObject renderObject,
                                    TileCoordinate tile) {
        WorldObject object = renderObject.object();
        return tile.x() >= object.x() && tile.x() < object.x() + renderObject.footprintWidth()
                && tile.y() >= object.y() && tile.y() < object.y() + renderObject.footprintLength();
    }

    private void notifyHover(double x, double y) {
        if (closed) return;
        hoverListener.accept(pick((float) x, (float) y).map(PickResult::tile));
    }

    private void redrawOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            redraw();
        } else {
            Platform.runLater(this::redraw);
        }
    }

    private void redraw() {
        if (closed || scene == null) return;
        int width = scene.document().width();
        int length = scene.document().length();
        canvas.setWidth(width * TILE_PIXELS);
        canvas.setHeight(length * TILE_PIXELS);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.web("#111827"));
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                TerrainMesh mesh = scene.terrainMeshes().get(coordinate);
                if (mesh != null) drawMesh(graphics, mesh, x, y,
                        scene.document().tile(coordinate).snapshot());
            }
        }
        drawDebugOverlays(graphics, width, length);
        for (WorldObject object : scene.objects()) {
            if (object.plane() != plane || object.x() < 0 || object.y() < 0
                    || object.x() >= width || object.y() >= length) continue;
            double centerX = (object.x() + 0.5) * TILE_PIXELS;
            double centerY = (object.y() + 0.5) * TILE_PIXELS;
            graphics.setFill(Color.web("#f97316"));
            graphics.fillOval(centerX - 3, centerY - 3, 6, 6);
        }
        drawSelections(graphics, width, length);
        drawRoutePreview(graphics);
        drawToolOverlay(graphics);
    }

    private void drawRoutePreview(GraphicsContext graphics) {
        if (routePreview == null || routePreview.start().plane() != plane) return;
        graphics.setLineWidth(3.0);
        graphics.setStroke(routePreview.successful()
                ? Color.web("#22c55e") : Color.web("#f43f5e"));
        if (routePreview.mode() == RoutePreviewMode.LINE_OF_SIGHT) {
            graphics.setLineDashes(4.0, 3.0);
        } else {
            graphics.setLineDashes();
        }
        var path = routePreview.path();
        for (int i = 1; i < path.size(); i++) {
            TileCoordinate from = path.get(i - 1);
            TileCoordinate to = path.get(i);
            graphics.strokeLine((from.x() + 0.5) * TILE_PIXELS,
                    (from.y() + 0.5) * TILE_PIXELS,
                    (to.x() + 0.5) * TILE_PIXELS,
                    (to.y() + 0.5) * TILE_PIXELS);
        }
        graphics.setLineDashes();
        drawPreviewMarker(graphics, routePreview.start(), Color.web("#38bdf8"), "S");
        drawPreviewMarker(graphics, routePreview.target(),
                routePreview.successful() ? Color.web("#22c55e") : Color.web("#f43f5e"), "T");
    }

    private static void drawPreviewMarker(GraphicsContext graphics, TileCoordinate tile,
                                          Color color, String label) {
        double centerX = (tile.x() + 0.5) * TILE_PIXELS;
        double centerY = (tile.y() + 0.5) * TILE_PIXELS;
        graphics.setFill(color);
        graphics.fillOval(centerX - 4, centerY - 4, 8, 8);
        graphics.setFill(Color.WHITE);
        graphics.setFont(Font.font(8));
        graphics.fillText(label, centerX - 2.5, centerY + 3);
    }

    private CollisionMap collisionMap() {
        CollisionMap map = new CollisionMap(scene.document().width(), scene.document().length(),
                scene.document().planes());
        scene.collision().forEach((coordinate, snapshot) -> {
            if (map.contains(coordinate)) map.set(coordinate, snapshot.rawFlags());
        });
        return map;
    }

    private void drawToolOverlay(GraphicsContext graphics) {
        EditorTool tool = toolController.activeTool();
        if (tool == null) return;
        tool.renderOverlay(new com.rspsi.editor.render.OverlayDraw() {
            @Override public void tileOutline(TileCoordinate tile) {
                if (tile.plane() != plane || tile.x() < 0 || tile.y() < 0
                        || scene == null || tile.x() >= scene.document().width()
                        || tile.y() >= scene.document().length()) return;
                graphics.setStroke(Color.color(0.25, 0.88, 1.0, 0.95));
                graphics.setLineWidth(1.5);
                graphics.strokeRect(tile.x() * TILE_PIXELS + 1, tile.y() * TILE_PIXELS + 1,
                        TILE_PIXELS - 2, TILE_PIXELS - 2);
            }

            @Override public void label(String text, float x, float y) {
                graphics.setFill(Color.color(0.96, 0.98, 1.0, 0.95));
                graphics.setFont(Font.font(9));
                graphics.fillText(text, x, y);
            }
        });
    }

    private void toolPointerDown(MouseEvent event) {
        requestFocus();
        if (toolController.activeTool() == null) return;
        toolController.pointerDown(pointerEvent(event));
        redrawOnFxThread();
    }

    private void toolPointerDrag(MouseEvent event) {
        if (toolController.activeTool() == null) return;
        toolController.pointerDrag(pointerEvent(event));
        redrawOnFxThread();
    }

    private void toolPointerUp(MouseEvent event) {
        if (toolController.activeTool() == null) return;
        toolController.pointerUp(pointerEvent(event));
        redrawOnFxThread();
    }

    private static PointerEvent pointerEvent(MouseEvent event) {
        return new PointerEvent((float) event.getX(), (float) event.getY(), button(event),
                event.isShiftDown(), event.isControlDown(), event.isAltDown());
    }

    private static PointerButton button(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY || event.isPrimaryButtonDown()) {
            return PointerButton.PRIMARY;
        }
        if (event.getButton() == MouseButton.SECONDARY || event.isSecondaryButtonDown()) {
            return PointerButton.SECONDARY;
        }
        if (event.getButton() == MouseButton.MIDDLE || event.isMiddleButtonDown()) {
            return PointerButton.MIDDLE;
        }
        return PointerButton.NONE;
    }

    private void drawDebugOverlays(GraphicsContext graphics, int width, int length) {
        if (debugOverlaySettings.modes().isEmpty()) return;
        if (worldWindow != null) drawGrid(graphics, width, length);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                if (debugOverlaySettings.enabled(DebugOverlayMode.TILE_FLAGS)) {
                    drawFlags(graphics, x, y, scene.document().tile(coordinate).snapshot());
                }
                if (debugOverlaySettings.enabled(DebugOverlayMode.COLLISION)) {
                    CollisionTileSnapshot collision = scene.collision().get(coordinate);
                    if (collision != null) drawCollision(graphics, x, y, collision);
                }
            }
        }
        if (debugOverlaySettings.enabled(DebugOverlayMode.BRIDGE_LINKS)) {
            scene.bridges().forEach(bridge -> {
                if (bridge.upper().plane() == plane || bridge.lower().plane() == plane) {
                    drawBridge(graphics, bridge.upper().x(), bridge.upper().y());
                }
            });
        }
    }

    private void drawGrid(GraphicsContext graphics, int width, int length) {
        drawGridLevel(graphics, width, length, DebugGridLevel.TILE, 1,
                Color.color(0.94, 0.96, 0.98, 0.10), 0.5);
        if (debugOverlaySettings.enabled(DebugOverlayMode.CHUNK_GRID)) {
            drawGridLevel(graphics, width, length, DebugGridLevel.CHUNK, 8,
                    Color.color(0.58, 0.65, 0.74, 0.38), 0.8);
        }
        if (debugOverlaySettings.enabled(DebugOverlayMode.REGION_GRID)) {
            drawGridLevel(graphics, width, length, DebugGridLevel.REGION, 64,
                    Color.color(0.97, 0.98, 1.0, 0.78), 1.5);
        }
        if (debugOverlaySettings.enabled(DebugOverlayMode.LOADED_WORLD_WINDOW)) {
            graphics.setStroke(Color.web("#38bdf8"));
            graphics.setLineWidth(1.5);
            graphics.strokeRect(0.75, 0.75, width * TILE_PIXELS - 1.5,
                    length * TILE_PIXELS - 1.5);
        }
    }

    private void drawGridLevel(GraphicsContext graphics, int width, int length,
                               DebugGridLevel level, int spacing, Color color, double lineWidth) {
        if (!debugOverlaySettings.enabled(mode(level))) return;
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        int originX = worldWindow.originX();
        int originY = worldWindow.originY();
        for (int x = 0; x <= width; x++) {
            if (Math.floorMod(originX + x, spacing) == 0) {
                graphics.strokeLine(x * TILE_PIXELS, 0, x * TILE_PIXELS, length * TILE_PIXELS);
            }
        }
        for (int y = 0; y <= length; y++) {
            if (Math.floorMod(originY + y, spacing) == 0) {
                graphics.strokeLine(0, y * TILE_PIXELS, width * TILE_PIXELS, y * TILE_PIXELS);
            }
        }
    }

    private static DebugOverlayMode mode(DebugGridLevel level) {
        return switch (level) {
            case TILE -> DebugOverlayMode.TILE_GRID;
            case CHUNK -> DebugOverlayMode.CHUNK_GRID;
            case REGION -> DebugOverlayMode.REGION_GRID;
            case WORLD_WINDOW -> DebugOverlayMode.LOADED_WORLD_WINDOW;
        };
    }

    private static void drawFlags(GraphicsContext graphics, int x, int y, TileSnapshot tile) {
        if (tile.flags() == 0) return;
        double px = x * TILE_PIXELS;
        double py = y * TILE_PIXELS;
        if ((tile.flags() & com.rspsi.editor.model.OsrsTileFlags.BLOCK_MAP_SQUARE) != 0) {
            graphics.setFill(Color.color(0.96, 0.25, 0.29, 0.18));
            graphics.fillRect(px, py, TILE_PIXELS, TILE_PIXELS);
        }
        graphics.setFill(Color.color(1.0, 0.96, 0.72, 0.92));
        graphics.setFont(Font.font(7));
        graphics.fillText(Integer.toHexString(tile.flags()).toUpperCase(), px + 1.0, py + 7.5);
    }

    private static void drawCollision(GraphicsContext graphics, int x, int y,
                                      CollisionTileSnapshot collision) {
        double left = x * TILE_PIXELS;
        double top = y * TILE_PIXELS;
        if (collision.floorBlocked()) {
            graphics.setFill(Color.color(0.98, 0.25, 0.29, 0.20));
            graphics.fillRect(left + 1, top + 1, TILE_PIXELS - 2, TILE_PIXELS - 2);
        }
        drawDirections(graphics, left, top, collision.movementBlocked(),
                Color.color(1.0, 0.30, 0.34, 0.95), 0.0, 1.4);
        drawDirections(graphics, left, top, collision.projectileBlocked(),
                Color.color(0.80, 0.45, 1.0, 0.95), 2.0, 0.7);
        if (collision.objectBlocked()) {
            graphics.setStroke(Color.color(1.0, 0.85, 0.35, 0.95));
            graphics.setLineWidth(0.9);
            graphics.strokeLine(left + 2, top + 2, left + TILE_PIXELS - 2, top + TILE_PIXELS - 2);
            graphics.strokeLine(left + TILE_PIXELS - 2, top + 2, left + 2, top + TILE_PIXELS - 2);
        }
    }

    private static void drawDirections(GraphicsContext graphics, double left, double top,
                                       java.util.Set<CollisionDirection> directions,
                                       Color color, double inset, double lineWidth) {
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        double right = left + TILE_PIXELS;
        double bottom = top + TILE_PIXELS;
        double centerX = left + TILE_PIXELS / 2.0;
        double centerY = top + TILE_PIXELS / 2.0;
        for (CollisionDirection direction : directions) {
            switch (direction) {
                case NORTH -> graphics.strokeLine(left + inset, top + inset, right - inset, top + inset);
                case SOUTH -> graphics.strokeLine(left + inset, bottom - inset, right - inset, bottom - inset);
                case EAST -> graphics.strokeLine(right - inset, top + inset, right - inset, bottom - inset);
                case WEST -> graphics.strokeLine(left + inset, top + inset, left + inset, bottom - inset);
                case NORTH_EAST -> graphics.strokeLine(centerX, centerY, right - inset, top + inset);
                case NORTH_WEST -> graphics.strokeLine(centerX, centerY, left + inset, top + inset);
                case SOUTH_EAST -> graphics.strokeLine(centerX, centerY, right - inset, bottom - inset);
                case SOUTH_WEST -> graphics.strokeLine(centerX, centerY, left + inset, bottom - inset);
            }
        }
    }

    private static void drawBridge(GraphicsContext graphics, int x, int y) {
        double centerX = (x + 0.5) * TILE_PIXELS;
        double centerY = (y + 0.5) * TILE_PIXELS;
        graphics.setStroke(Color.color(0.22, 0.83, 0.97, 0.95));
        graphics.setLineWidth(1.4);
        graphics.strokeOval(centerX - 3, centerY - 3, 6, 6);
    }

    private void drawSelections(GraphicsContext graphics, int width, int length) {
        if (session == null) return;
        graphics.setStroke(Color.web("#f8fafc"));
        graphics.setLineWidth(2.0);
        for (var selected : session.selection().tiles()) {
            if (selected.plane() != plane || selected.x() >= width || selected.y() >= length) continue;
            graphics.strokeRect(selected.x() * TILE_PIXELS + 1, selected.y() * TILE_PIXELS + 1,
                    TILE_PIXELS - 2, TILE_PIXELS - 2);
        }
    }

    private static void drawMesh(GraphicsContext graphics, TerrainMesh mesh,
                                  int tileX, int tileY, TileSnapshot tile) {
        for (TerrainFace face : mesh.faces()) {
            TerrainVertex a = mesh.vertices().get(face.a());
            TerrainVertex b = mesh.vertices().get(face.b());
            TerrainVertex c = mesh.vertices().get(face.c());
            graphics.setFill(materialColor(face.material() == 1
                    ? tile.overlayId() : tile.underlayId(), a.height(), b.height(), c.height()));
            graphics.fillPolygon(
                    new double[] {pixel(tileX, a.x()), pixel(tileX, b.x()), pixel(tileX, c.x())},
                    new double[] {pixel(tileY, a.y()), pixel(tileY, b.y()), pixel(tileY, c.y())}, 3);
        }
    }

    private static double pixel(int tile, int local) {
        return (tile + local / 128.0) * TILE_PIXELS;
    }

    private static Color materialColor(int id, int... heights) {
        int sum = 0;
        for (int height : heights) sum += height;
        double shade = Math.max(0.60, Math.min(1.0, 0.84 - (sum / (double) heights.length) / 4096.0));
        double hue = Math.floorMod(id * 37, 360);
        return Color.hsb(hue, 0.34, shade);
    }

    private void closeBinding() {
        if (sceneController != null) {
            sceneController.close();
            sceneController = null;
        }
        toolController.deactivate();
        if (session != null) session.selection().removeChangeListener(selectionListener);
        session = null;
        worldWindow = null;
        scene = null;
        routePreview = null;
        inputRouter = null;
        hoverListener = ignored -> { };
        assets = EmptyAssetRepository.INSTANCE;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeBinding();
    }

}
