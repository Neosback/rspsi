package com.rspsi.ui.workspace;

import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuUploadPlanBuilder;
import com.rspsi.editor.render.RenderConfig;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.settings.SettingChange;
import com.rspsi.editor.settings.SettingsStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Hosts the Map Editor viewport.
 *
 * <p>The production scene surface is the embedded LWJGL/OpenGL viewport. The
 * canonical scene adapter is retained as a non-visual scene/query host while
 * the GPU packet is assembled; it is intentionally not mounted as a visible
 * fallback when native rendering is unavailable.</p>
 */
public final class ControlledViewportPanel extends StackPane implements AutoCloseable {
    private final Node legacyViewport;
    private final CanonicalSceneViewport canonicalViewport = new CanonicalSceneViewport();
    private EmbeddedOpenGlViewport openGlViewport;
    private QuickLaunchCard quickLaunch;
    private GpuScenePacket sourcePacket;
    private SettingsStore renderSettings;
    private Consumer<SettingChange> renderSettingsListener;
    private AutoCloseable renderSettingsCleanup;

    public ControlledViewportPanel(Node legacyViewport) {
        this.legacyViewport = Objects.requireNonNull(legacyViewport, "legacyViewport");
        getStyleClass().add("controlled-viewport-panel");
        // Do not mount the legacy JavaFX Canvas while the Map Editor is
        // waiting for a project. The normal shell therefore has no hidden
        // renderer fallback and does not spend time drawing an unused scene.
        Label waiting = new Label("Open a project to begin");
        waiting.getStyleClass().add("workspace-panel-status");
        StackPane waitingSurface = new StackPane(waiting);
        waitingSurface.setAlignment(Pos.CENTER);
        waitingSurface.setAccessibleText("Map Editor is waiting for a project");
        getChildren().add(waitingSurface);
    }

    public void installQuickLaunch(QuickLaunchHandler handler) {
        if (quickLaunch != null) getChildren().remove(quickLaunch);
        quickLaunch = new QuickLaunchCard(handler);
        StackPane.setAlignment(quickLaunch, Pos.CENTER);
        getChildren().add(quickLaunch);
    }

    public void setWaitingForInput(boolean waiting) {
        if (quickLaunch != null) {
            quickLaunch.setVisible(waiting);
            quickLaunch.setManaged(waiting);
        }
    }

    public void recordRecent(String value) {
        if (quickLaunch != null) quickLaunch.recordRecent(value);
    }

    public CanonicalSceneViewport canonicalViewport() {
        return canonicalViewport;
    }

    /** Lazily creates the production OpenGL surface without opening a context at shell startup. */
    public EmbeddedOpenGlViewport openGlViewport() {
        if (openGlViewport == null) openGlViewport = new EmbeddedOpenGlViewport();
        return openGlViewport;
    }

    /** Shows an immutable GPU packet while retaining the canonical session separately. */
    public void showOpenGl(GpuScenePacket packet) {
        Objects.requireNonNull(packet, "packet");
        clearRenderSettingsBinding();
        sourcePacket = packet;
        EmbeddedOpenGlViewport surface = openGlViewport();
        surface.setPresentation(RenderPresentation.neutral());
        surface.upload(new GpuUploadPlanBuilder().build(packet));
        getChildren().setAll(surface);
    }

    /** Shows a packet projection compiled from the shared typed settings store. */
    public void showOpenGl(GpuScenePacket sourcePacket, SettingsStore settings) {
        showOpenGl(sourcePacket, settings, null);
    }

    /** Shows a packet projection and owns an optional compatibility binding. */
    public void showOpenGl(GpuScenePacket sourcePacket, SettingsStore settings,
                           AutoCloseable settingsCleanup) {
        this.sourcePacket = Objects.requireNonNull(sourcePacket, "source packet");
        clearRenderSettingsBinding();
        this.renderSettings = Objects.requireNonNull(settings, "render settings");
        this.renderSettingsCleanup = settingsCleanup;
        renderSettingsListener = change -> applyRenderSettings();
        renderSettings.addListener(renderSettingsListener);
        applyRenderSettings();
        getChildren().setAll(openGlViewport());
    }

    /**
     * Shows a diagnostic surface when the required native renderer cannot be
     * created. This is deliberately not a JavaFX scene-rendering fallback:
     * silently switching renderer semantics would make the Map Editor look
     * usable while producing a different result from the production pipeline.
     */
    public void showOpenGlUnavailable(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        clearRenderSettingsBinding();
        sourcePacket = null;
        Label title = new Label("OpenGL 3.3 viewport unavailable");
        title.getStyleClass().add("workspace-panel-title");
        Label detail = new Label("The Map Editor requires the embedded LWJGL/OpenGL surface."
                + " Check the graphics driver and restart the application.");
        detail.setWrapText(true);
        detail.setMaxWidth(520);
        detail.getStyleClass().add("workspace-panel-status");
        Label reason = new Label(failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage());
        reason.setWrapText(true);
        reason.setMaxWidth(520);
        reason.getStyleClass().add("workspace-diagnostic-detail");
        VBox content = new VBox(8, title, detail, reason);
        content.setPadding(new Insets(24));
        content.setMaxWidth(560);
        content.getStyleClass().add("workspace-placeholder");
        StackPane surface = new StackPane(content);
        surface.setAlignment(Pos.CENTER);
        surface.setAccessibleText("OpenGL 3.3 viewport unavailable");
        getChildren().setAll(surface);
    }

    private void applyRenderSettings() {
        if (sourcePacket == null || renderSettings == null) return;
        RenderConfig config = new com.rspsi.editor.render.RenderConfigCompiler()
                .compile(renderSettings.snapshot());
        EmbeddedOpenGlViewport surface = openGlViewport();
        surface.setPresentation(config.presentation());
        surface.upload(new GpuUploadPlanBuilder().build(config.apply(sourcePacket)));
    }

    private void clearRenderSettingsBinding() {
        if (renderSettings != null && renderSettingsListener != null) {
            renderSettings.removeListener(renderSettingsListener);
        }
        renderSettings = null;
        renderSettingsListener = null;
        if (renderSettingsCleanup != null) {
            try {
                renderSettingsCleanup.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to close renderer settings binding", exception);
            }
        }
        renderSettingsCleanup = null;
    }

    /** Explicit compatibility entry point for legacy sessions; never used by the OSRS project flow. */
    public void showLegacy() {
        clearRenderSettingsBinding();
        sourcePacket = null;
        canonicalViewport.close();
        getChildren().clear();
        getChildren().add(legacyViewport);
        if (quickLaunch != null) getChildren().add(quickLaunch);
    }

    @Override
    public void close() {
        clearRenderSettingsBinding();
        sourcePacket = null;
        canonicalViewport.close();
        if (openGlViewport != null) openGlViewport.close();
    }
}
