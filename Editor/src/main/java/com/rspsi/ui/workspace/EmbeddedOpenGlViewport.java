package com.rspsi.ui.workspace;

import com.rspsi.renderer.opengl.OpenGlSceneRenderer;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuSceneUploader;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuPlanPicker;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderPresentation;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import javafx.embed.swing.SwingNode;
import javafx.scene.layout.StackPane;

import javax.swing.SwingUtilities;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * JavaFX host for the production OpenGL viewport.
 *
 * <p>JavaFX owns the shell and layout. The AWT/LWJGL canvas owns only the
 * native OpenGL context and presents the immutable {@link GpuUploadPlan}; it
 * never opens a cache or constructs scene state. Software and JavaFX scene
 * renderers may remain useful for headless/reference tests, but are not a
 * production fallback for this viewport.</p>
 */
public final class EmbeddedOpenGlViewport extends StackPane
        implements GpuSceneUploader, AutoCloseable {
    private final SwingNode swingNode = new SwingNode();
    private final AtomicReference<GpuUploadPlan> pendingPlan = new AtomicReference<>();
    private volatile CameraState camera = new CameraState(0, 0, 0, 0, 0);
    private volatile RenderPresentation presentation = RenderPresentation.neutral();
    private volatile IntConsumer animationTick = ignored -> { };
    private volatile GlCanvas canvas;
    private volatile boolean closed;
    private final GpuPlanPicker picker = new GpuPlanPicker();

    public EmbeddedOpenGlViewport() {
        getStyleClass().add("embedded-opengl-viewport");
        setAccessibleText("OpenGL OSRS scene viewport");
        getChildren().add(swingNode);
        widthProperty().addListener((observable, oldValue, newValue) -> requestRender());
        heightProperty().addListener((observable, oldValue, newValue) -> requestRender());
        SwingUtilities.invokeLater(this::createCanvas);
    }

    private void createCanvas() {
        if (closed) return;
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        // MSAA is an optional presentation feature, not part of the GL 3.3
        // compatibility contract. Do not make surface creation fail on a
        // driver that exposes core 3.3 but no multisample default framebuffer;
        // a negotiated MSAA FBO can be added later without changing packets.
        data.samples = 0;
        data.swapInterval = 1;
        GlCanvas created = new GlCanvas(data, cycle -> this.animationTick.accept(cycle));
        created.setIgnoreRepaint(true);
        created.setFocusable(true);
        created.setCamera(camera);
        canvas = created;
        JPanel host = new JPanel(new BorderLayout());
        host.setOpaque(false);
        host.add(created, BorderLayout.CENTER);
        swingNode.setContent(host);
        GpuUploadPlan plan = pendingPlan.get();
        if (plan != null) created.setPlan(plan);
        created.setPresentation(presentation);
    }

    /** Updates the render camera without changing the canonical scene. */
    public void setCamera(CameraState camera) {
        this.camera = Objects.requireNonNull(camera, "camera");
        GlCanvas current = canvas;
        if (current != null) {
            SwingUtilities.invokeLater(() -> current.setCamera(camera));
        }
    }

    public CameraState camera() {
        return camera;
    }

    public void setPresentation(RenderPresentation presentation) {
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        GlCanvas current = canvas;
        if (current != null) {
            SwingUtilities.invokeLater(() -> current.setPresentation(presentation));
        }
    }

    /** Installs a client-cycle callback used to refresh derived animated geometry. */
    public void setAnimationTick(IntConsumer animationTick) {
        this.animationTick = Objects.requireNonNull(animationTick, "animationTick");
    }

    @Override
    public void upload(GpuUploadPlan plan) {
        pendingPlan.set(Objects.requireNonNull(plan, "plan"));
        GlCanvas current = canvas;
        if (current != null) SwingUtilities.invokeLater(() -> current.setPlan(plan));
    }

    @Override
    public void invalidate(Set<TileCoordinate> tiles) {
        Objects.requireNonNull(tiles, "tiles");
        GlCanvas current = canvas;
        if (current != null) SwingUtilities.invokeLater(current::requestRender);
    }

    /** Requests a redraw after a layout or camera change. */
    public void requestRender() {
        GlCanvas current = canvas;
        if (current != null) SwingUtilities.invokeLater(current::requestRender);
    }

    /** True once the native canvas has been created and attached. */
    public boolean isSurfaceCreated() {
        return canvas != null && !closed;
    }

    /**
     * Picks against the same immutable plan submitted to OpenGL. This is a
     * neutral correctness path until the native ID-buffer pass is added;
     * callers receive the same tile/object identity either way.
     */
    public Optional<PickResult> pick(float x, float y) {
        GpuUploadPlan plan = pendingPlan.get();
        GlCanvas current = canvas;
        if (plan == null || current == null || closed) return Optional.empty();
        return picker.pick(plan, camera, Math.max(1, current.getWidth()),
                Math.max(1, current.getHeight()), x, y);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        GlCanvas current = canvas;
        canvas = null;
        if (current != null) {
            SwingUtilities.invokeLater(() -> {
                current.disposeCanvas();
                swingNode.setContent(null);
            });
        }
    }

    private static final class GlCanvas extends AWTGLCanvas {
        private final OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
        private final IntConsumer animationTick;
        private volatile GpuUploadPlan plan;
        private volatile CameraState camera = new CameraState(0, 0, 0, 0, 0);
        private volatile RenderPresentation presentation = RenderPresentation.neutral();
        private volatile int clientCycle;
        private volatile boolean repaintRequested = true;
        private javax.swing.Timer animationTimer;
        private int dragX;
        private int dragY;

        private GlCanvas(GLData data, IntConsumer animationTick) {
            super(data);
            this.animationTick = Objects.requireNonNull(animationTick, "animationTick");
            MouseAdapter controls = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    dragX = event.getX();
                    dragY = event.getY();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    int deltaX = event.getX() - dragX;
                    int deltaY = event.getY() - dragY;
                    dragX = event.getX();
                    dragY = event.getY();
                    CameraState current = camera;
                    float pitch = Math.max(-1.45f, Math.min(1.45f,
                            current.pitch() - deltaY * 0.006f));
                    float yaw = current.yaw() - deltaX * 0.006f;
                    camera = new CameraState(current.x(), current.y(), current.z(), pitch, yaw);
                    requestRender();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    CameraState current = camera;
                    float distance = (float) (event.getPreciseWheelRotation() * 256.0);
                    float sinYaw = (float) Math.sin(current.yaw());
                    float cosYaw = (float) Math.cos(current.yaw());
                    camera = new CameraState(current.x() + sinYaw * distance,
                            current.y() + distance * 0.35f,
                            current.z() + cosYaw * distance,
                            current.pitch(), current.yaw());
                    requestRender();
                }
            };
            addMouseListener(controls);
            addMouseMotionListener(controls);
            addMouseWheelListener(controls);
        }

        private void setPlan(GpuUploadPlan plan) {
            this.plan = Objects.requireNonNull(plan, "plan");
            requestRender();
        }

        private void setCamera(CameraState camera) {
            this.camera = Objects.requireNonNull(camera, "camera");
            requestRender();
        }

        private void setPresentation(RenderPresentation presentation) {
            this.presentation = Objects.requireNonNull(presentation, "presentation");
            requestRender();
        }

        private void setClientCycle(int clientCycle) {
            this.clientCycle = Math.max(0, clientCycle);
            requestRender();
        }

        @Override
        protected void requestRender() {
            repaintRequested = true;
            if (!isDisplayable()) return;
            // AWTGLCanvas' default paint path only calls requestRender().  The
            // embedded surface therefore has to enter the GL render path here;
            // repainting alone can leave the canvas with a valid context but
            // no presented frame.
            if (SwingUtilities.isEventDispatchThread()) {
                render();
            } else {
                SwingUtilities.invokeLater(this::render);
            }
        }

        @Override
        public void initGL() {
            renderer.initialize();
            animationTimer = new javax.swing.Timer(20, ignored -> {
                int cycle = clientCycle();
                setClientCycle(cycle);
                animationTick.accept(cycle);
                requestRender();
            });
            animationTimer.setCoalesce(true);
            animationTimer.start();
        }

        private static int clientCycle() {
            return (int) ((System.nanoTime() / 1_000_000L) / 20L);
        }

        @Override
        public void paintGL() {
            if (!repaintRequested && plan == null) return;
            repaintRequested = false;
            renderer.draw(plan, camera, Math.max(1, getFramebufferWidth()),
                    Math.max(1, getFramebufferHeight()), presentation, clientCycle);
            swapBuffers();
        }

        @Override
        protected void disposeGL() {
            if (animationTimer != null) {
                animationTimer.stop();
                animationTimer = null;
            }
            renderer.close();
        }
    }
}
