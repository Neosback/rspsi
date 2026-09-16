package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.ui.StandardWorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceCatalog;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compatibility bridge that reuses the existing FXML regions in the new
 * constrained workspace shell. It is deliberately opt-in while launch/load,
 * edit, save, and autosave coverage is still being migrated.
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
        detach(controller.getLegacyInspector());
        detach(controller.getGrabBar());

        controller.getLegacyToolRail().setMaxWidth(Double.MAX_VALUE);
        controller.getLegacyToolRail().setMaxHeight(Double.MAX_VALUE);
        controller.getLegacyViewport().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        controller.getLegacyInspector().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Map<String, Node> panels = new LinkedHashMap<>();
        panels.put("tools", controller.getLegacyToolRail());
        panels.put("viewport", new ControlledViewportPanel(controller.getLegacyViewport()));
        panels.put("assets", assets == null
                ? controller.getLegacyInspector()
                : new AssetBrowserPanel(assets));
        panels.put("inspector", new SessionInspectorPanel(new LegacyDefinitionProvider()));
        panels.put("history", new SessionHistoryPanel());
        panels.put("validation", new ValidationPanel());
        panels.put("console", placeholder("Console", "Editor messages will appear here."));
        panels.put("command-palette", placeholder("Command palette", "Search commands with Cmd/Ctrl-P."));

        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();
        ControlledWorkspaceShell shell = new ControlledWorkspaceShell(
                catalog, catalog.workspace("map"), panels);
        shell.setTop(controller.getGrabBar());
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
     * layer. The legacy renderer remains a separate compatibility viewport.
     */
    public static void bindProject(ControlledWorkspaceShell shell,
                                   OsrsProjectSessionLoader.OpenedProject opened,
                                   DefinitionProvider definitions, AssetRepository assets) {
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
            viewport.showCanonical(session, window);
            if (shell.panelNode("inspector") instanceof SessionInspectorPanel inspector) {
                viewport.canonicalViewport().setHoverListener(hover -> {
                    if (hover.isEmpty() || session.selection().current() != null) {
                        if (hover.isEmpty()) inspector.refresh();
                        return;
                    }
                    inspector.showTile(hover.get());
                });
            }
        }
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
