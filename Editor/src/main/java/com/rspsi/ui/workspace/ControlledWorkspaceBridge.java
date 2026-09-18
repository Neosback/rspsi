package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginLoader;
import com.rspsi.editor.plugin.EditorPluginStateStore;
import com.rspsi.editor.plugin.PluginDiscovery;
import com.rspsi.editor.plugin.builtin.CoreToolsPlugin;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuScenePacketBuilder;
import com.rspsi.editor.render.GpuUploadPlanBuilder;
import com.rspsi.editor.render.RenderConfig;
import com.rspsi.editor.render.SceneWindow;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.StandardWorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceCatalog;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.application.Platform;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility bridge that reuses the existing FXML regions in the modern
 * constrained workspace shell while the viewport and specialized panels are
 * migrated incrementally.
 */
public final class ControlledWorkspaceBridge {
    private ControlledWorkspaceBridge() {
    }

    public static Parent adapt(Parent loadedContent, MainController controller) {
        return adapt(loadedContent, controller, null);
    }

    /** Uses the neutral asset browser when an OSRS repository is available. */
    public static Parent adapt(Parent loadedContent, MainController controller,
                               AssetRepository assets) {
        Objects.requireNonNull(loadedContent, "loadedContent");
        Objects.requireNonNull(controller, "controller");
        if (controller.getLegacyToolRail() == null
                || controller.getLegacyViewport() == null
                || controller.getLegacyInspector() == null
                || controller.getGrabBar() == null) {
            throw new IllegalStateException("main_test4.fxml lacks controlled workspace regions");
        }

        detach(controller.getLegacyToolRail());
        detach(controller.getLegacyViewport());
        detach(controller.getLegacyViewportToolBar());
        detach(controller.getLegacyInspector());
        detach(controller.getGrabBar());

        // The legacy viewport includes an empty title strip and a bottom
        // toolbar. The toolbar is rehosted below the viewport; remove only
        // the empty strip so the game canvas can use the available height.
        if (controller.getLegacyViewport().getChildren().size() > 1
                && controller.getLegacyViewport().getChildren().get(0) instanceof javafx.scene.layout.HBox) {
            controller.getLegacyViewport().getChildren().remove(0);
        }
        detach(controller.getToolsTabPane());

        controller.getLegacyToolRail().setMaxWidth(Double.MAX_VALUE);
        controller.getLegacyToolRail().setMaxHeight(Double.MAX_VALUE);
        controller.getLegacyViewport().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        controller.getLegacyInspector().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        CompactToolRail tools = new CompactToolRail(controller);
        MapToolContextPanel toolContext = new MapToolContextPanel(
                controller, controller.getLegacyViewportToolBar());
        AssetBrowserPanel browser = assets == null ? null : new AssetBrowserPanel(assets);
        Map<String, Node> panels = new LinkedHashMap<>();
        panels.put("tools", tools);
        panels.put("context-toolbar", new ContextToolbar());
        panels.put("tool-context", toolContext);
        panels.put("selector-strip", new SelectorStrip(controller));
        panels.put("viewport", new ControlledViewportPanel(controller.getLegacyViewport()));
        panels.put("outliner", new WorldOutlinerPanel());
        panels.put("assets", browser == null ? controller.getLegacyInspector() : browser);
        panels.put("floor-palette", controller.getToolsTabPane());
        panels.put("inspector", new SessionInspectorPanel(new LegacyDefinitionProvider()));
        RightToolRail rightToolRail = new RightToolRail();
        RightSettingsPanel settingsPanel = new RightSettingsPanel(
                panels.get("outliner"), panels.get("inspector"));
        rightToolRail.onCategorySelected(settingsPanel::showCategory);
        panels.put("right-tool-rail", rightToolRail);
        panels.put("settings-panel", settingsPanel);
        panels.put("history", new SessionHistoryPanel());
        panels.put("validation", new ValidationPanel());
        panels.put("console", placeholder("Console", "Editor messages will appear here."));
        panels.put("plugins", new PluginsPanel());
        panels.put("command-palette", new PluginCommandPalettePanel());

        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();
        ControlledWorkspaceShell shell = new ControlledWorkspaceShell(
                catalog, catalog.workspace("map"), panels);
        shell.setTopBar(ModernMenuBarFactory.create(controller, shell), controller.getControlBox());
        shell.setStatusBar(new WorkspaceStatusBar());
        return shell;
    }

    /** Binds any canonical session to the common workspace panels. */
    public static void bindSession(ControlledWorkspaceShell shell, EditorSession session,
                                   WorldWindow window, DefinitionProvider definitions) {
        bindSession(shell, session, window, definitions,
                "Compatibility scene", "Cache: legacy", "");
    }

    /** Binds a session with explicit status-bar context supplied by its host. */
    public static void bindSession(ControlledWorkspaceShell shell, EditorSession session,
                                   WorldWindow window, DefinitionProvider definitions,
                                   String context, String cache, String compatibility) {
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(window, "window");
        if (shell.panelNode("history") instanceof SessionHistoryPanel history) {
            history.bind(session);
        }
        if (shell.panelNode("tools") instanceof AdaptiveToolPanel tools) {
            tools.showLegacy();
        }
        if (shell.panelNode("inspector") instanceof SessionInspectorPanel inspector) {
            inspector.setDefinitionProvider(definitions);
            inspector.bind(session, window);
        }
        if (shell.panelNode("validation") instanceof ValidationPanel validation) {
            validation.bind(session, definitions);
        }
        if (shell.statusBar() instanceof WorkspaceStatusBar status) {
            status.bind(session, context, cache, compatibility);
        }
    }

    /**
     * Binds an OSRS project opened through the cache/session composition
     * layer. The Map Editor uses the embedded OpenGL surface as its only
     * production scene renderer.
     */
    public static void bindProject(ControlledWorkspaceShell shell,
                                   OsrsProjectSessionLoader.OpenedProject opened,
                                   DefinitionProvider definitions, AssetRepository assets) {
        bindProject(shell, opened, definitions, assets, EditorPluginStateStore.defaultStore());
    }

    /** Binds a project with an explicit plugin state store (tests pass a temp store). */
    public static void bindProject(ControlledWorkspaceShell shell,
                                   OsrsProjectSessionLoader.OpenedProject opened,
                                   DefinitionProvider definitions, AssetRepository assets,
                                   EditorPluginStateStore pluginState) {
        Objects.requireNonNull(opened, "opened");
        EditorSession session = opened.region().session();
        WorldWindow window = new WorldWindow(opened.region().regionX() * 64,
                opened.region().regionY() * 64,
                session.world().width(), session.world().length());
        String region = "Region " + opened.region().regionId()
                + " (" + opened.region().regionX() + "," + opened.region().regionY() + ")";
        String cache = "Cache revision " + opened.project().cacheRevision();
        String compatibility = opened.compatibility().issues().isEmpty()
                ? "" : String.join(", ", opened.compatibility().issues());
        bindSession(shell, session, window, definitions, region, cache, compatibility);
        if (assets != null && shell.panelNode("assets") instanceof AssetBrowserPanel browser) {
            browser.setRepository(assets);
        }
        if (shell.panelNode("viewport") instanceof ControlledViewportPanel viewport) {
            try {
                // Bind the canonical scene adapter only as the neutral source
                // for packet construction and plugin queries. It is never
                // mounted, so JavaFX Canvas cannot silently become the scene
                // renderer again.
                viewport.canonicalViewport().bind(session, window, definitions, assets);
                SceneWindow gpuWindow = SceneWindow.from(new WorldRegionWindow(
                        opened.region().regionX(), opened.region().regionY(), 1, 1,
                        Map.of(opened.worldRegion().regionId(), opened.worldRegion())));
                SettingsStore renderSettings = LegacyRenderSettingsAdapter.createStore();
                LegacyRenderSettingsAdapter.LegacyBinding settingsBinding =
                        LegacyRenderSettingsAdapter.bindOptions(renderSettings);
                GpuScenePacket gpuPacket = new GpuScenePacketBuilder().build(
                        gpuWindow, viewport.canonicalViewport().sceneSnapshot());
                double centerX = window.originX() * 128.0 + window.width() * 64.0;
                double centerZ = window.originY() * 128.0 + window.length() * 64.0;
                viewport.openGlViewport().setCamera(new CameraState(
                        (float) centerX, 2400.0f, (float) centerZ - 4200.0f,
                        (float) -Math.toRadians(28.0), 0.0f));
                viewport.showOpenGl(gpuPacket, renderSettings, settingsBinding);
                installAnimationRefresh(viewport, gpuWindow, renderSettings);
            } catch (RuntimeException nativeRendererFailure) {
                // Do not change rendering semantics behind the user's back.
                // A driver/context failure is actionable and must remain
                // visible instead of becoming an unannounced Canvas preview.
                System.err.println("Embedded OpenGL viewport unavailable: "
                        + nativeRendererFailure.getMessage());
                viewport.showOpenGlUnavailable(nativeRendererFailure);
            }
            if (shell.panelNode("tools") instanceof AdaptiveToolPanel tools) {
                tools.showCanonical(viewport.canonicalViewport());
                List<EditorPlugin> pluginsToLoad = new ArrayList<>();
                pluginsToLoad.addAll(CoreToolsPlugin.builtIns());
                PluginDiscovery discovery = EditorPluginLoader.discoverOwned(
                        java.nio.file.Path.of("plugins"),
                        Thread.currentThread().getContextClassLoader());
                pluginsToLoad.addAll(discovery.plugins());
                EditorPluginLifecycleManager lifecycle = EditorPluginLifecycleManager.start(
                        pluginsToLoad,
                        pluginState == null
                                ? EditorPluginStateStore.defaultStore() : pluginState,
                        session,
                        assets == null ? EmptyAssetRepository.INSTANCE : assets,
                        viewport.canonicalViewport()::sceneSnapshotView,
                        candidates -> EditorPluginHost.initialize(
                                candidates,
                                session,
                                assets == null ? EmptyAssetRepository.INSTANCE : assets,
                                viewport.canonicalViewport()::sceneSnapshotView),
                        discovery);
                EditorPluginHost plugins = lifecycle.host();
                if (shell.panelNode("plugins") instanceof PluginsPanel pluginsPanel) {
                    pluginsPanel.bindPluginHost(lifecycle);
                    pluginsPanel.onHostRebuilt(shell::bindPluginHost);
                }
                shell.bindPluginHost(plugins);
                if (shell.panelNode("assets") instanceof AssetBrowserPanel browser) {
                    browser.selectedAsset().ifPresent(tools::setObjectAsset);
                }
            }
            if (shell.panelNode("inspector") instanceof SessionInspectorPanel inspector) {
                viewport.canonicalViewport().setHoverListener(hover -> {
                    if (shell.statusBar() instanceof WorkspaceStatusBar status) {
                        status.setHover(window, hover);
                    }
                    if (hover.isEmpty() || session.selection().current() != null) {
                        if (hover.isEmpty()) inspector.refresh();
                        return;
                    }
                    inspector.showTile(hover.get());
                });
            }
        }
    }

    /** Connects the native client-cycle timer to derived animated scene packets. */
    private static void installAnimationRefresh(ControlledViewportPanel viewport,
                                                SceneWindow window,
                                                SettingsStore settings) {
        // Texture animation is evaluated in the OpenGL shader. Rebuilding the
        // complete scene every client cycle is only necessary for animated
        // model geometry, and is prohibitively expensive for a normal map
        // region. Keep the native viewport responsive when a scene has no
        // CPU-animated models at all.
        if (viewport.canonicalViewport().sceneSnapshot() == null
                || viewport.canonicalViewport().sceneSnapshot().modelPackets().stream()
                .noneMatch(com.rspsi.editor.render.ModelRenderPacket::supportsAnimation)) {
            return;
        }
        AtomicBoolean queued = new AtomicBoolean();
        AtomicInteger lastCycle = new AtomicInteger(-1);
        viewport.openGlViewport().setAnimationTick(cycle -> {
            int previous = lastCycle.get();
            if (previous >= 0 && cycle - previous < 5) return;
            if (!queued.compareAndSet(false, true)) return;
            Platform.runLater(() -> {
                try {
                    if (cycle == lastCycle.get()) return;
                    CanonicalSceneViewport canonical = viewport.canonicalViewport();
                    canonical.refreshAnimation(cycle);
                    GpuScenePacket packet = new GpuScenePacketBuilder().build(
                            window, canonical.sceneSnapshot());
                    RenderConfig config = new com.rspsi.editor.render.RenderConfigCompiler()
                            .compile(settings.snapshot());
                    viewport.openGlViewport().upload(
                            new GpuUploadPlanBuilder().build(config.apply(packet)));
                    lastCycle.set(cycle);
                } finally {
                    queued.set(false);
                }
            });
        });
    }

    /** Returns the mounted status bar for frontend lifecycle management. */
    public static WorkspaceStatusBar statusBar(ControlledWorkspaceShell shell) {
        Objects.requireNonNull(shell, "shell");
        return shell.statusBar() instanceof WorkspaceStatusBar status ? status : null;
    }

    private static Label placeholder(String title, String message) {
        Label label = new Label(title + "\n" + message);
        label.setWrapText(true);
        label.getStyleClass().add("workspace-placeholder");
        label.setAccessibleText(title + ". " + message);
        return label;
    }

    private static void detach(Node node) {
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
