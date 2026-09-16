package com.rspsi.ui.workspace;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SelectionChangeListener;
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
                if (session != null && session.selection().contains(coordinate)) {
                    graphics.setStroke(Color.web("#f8fafc"));
                    graphics.setLineWidth(2.0);
                    graphics.strokeRect(x * TILE_PIXELS + 1, y * TILE_PIXELS + 1,
                            TILE_PIXELS - 2, TILE_PIXELS - 2);
                }
            }
        }
        for (WorldObject object : scene.objects()) {
            if (object.plane() != plane || object.x() < 0 || object.y() < 0
                    || object.x() >= width || object.y() >= length) continue;
            double centerX = (object.x() + 0.5) * TILE_PIXELS;
            double centerY = (object.y() + 0.5) * TILE_PIXELS;
            graphics.setFill(Color.web("#f97316"));
            graphics.fillOval(centerX - 3, centerY - 3, 6, 6);
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
