package com.rspsi.ui.workspace;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SelectionChangeListener;
import com.rspsi.editor.collision.CollisionDirection;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.debug.DebugGridLevel;
import com.rspsi.editor.debug.DebugOverlayMode;
import com.rspsi.editor.debug.DebugOverlaySettings;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderChanges;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.SceneRenderer;
import com.rspsi.editor.render.SessionSceneController;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainVertex;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.Objects;
import java.util.Optional;

/**
 * Small JavaFX adapter for the canonical scene model.
 *
 * <p>This is a top-down semantic preview, not the replacement 3D renderer.
 * It gives the controlled OSRS project workflow a real neutral scene surface
 * while the faithful legacy/GPU renderer remains a separately gated task.</p>
 */
public final class CanonicalSceneViewport extends StackPane implements SceneRenderer, AutoCloseable {
    private static final double TILE_PIXELS = 10.0;

    private final Canvas canvas = new Canvas();
    private final SelectionChangeListener selectionListener = ignored -> redrawOnFxThread();
    private EditorSession session;
    private WorldWindow worldWindow;
    private RenderScene scene;
    private SessionSceneController sceneController;
    private int plane;
    private DebugOverlaySettings debugOverlaySettings = DebugOverlaySettings.none();
    private boolean closed;

    public CanonicalSceneViewport() {
        getStyleClass().add("canonical-scene-viewport");
        setAccessibleText("Canonical OSRS scene preview");
        setFocusTraversable(true);
        canvas.setOnMouseClicked(event -> pickAndSelect(event.getX(), event.getY()));
        getChildren().add(canvas);
    }

    public void bind(EditorSession session, WorldWindow worldWindow) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(worldWindow, "worldWindow");
        closeBinding();
        this.session = session;
        this.worldWindow = worldWindow;
        session.selection().addChangeListener(selectionListener);
        closed = false;
        scene = null;
        sceneController = new SessionSceneController(session, this);
        redrawOnFxThread();
    }

    public EditorSession session() {
        return session;
    }

    public WorldWindow worldWindow() {
        return worldWindow;
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

    private void pickAndSelect(double x, double y) {
        if (session == null) return;
        pick((float) x, (float) y).ifPresent(result -> {
            session.selection().select(result.tile());
            requestFocus();
        });
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
        if (session != null) session.selection().removeChangeListener(selectionListener);
        session = null;
        worldWindow = null;
        scene = null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeBinding();
    }
}
