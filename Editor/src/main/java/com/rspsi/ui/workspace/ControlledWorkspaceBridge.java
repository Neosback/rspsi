package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
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
        panels.put("viewport", controller.getLegacyViewport());
        panels.put("assets", controller.getLegacyInspector());
        panels.put("inspector", new SessionInspectorPanel(new LegacyDefinitionProvider()));
        panels.put("history", new SessionHistoryPanel());
        panels.put("validation", new ValidationPanel());
        panels.put("console", placeholder("Console", "Editor messages will appear here."));
        panels.put("command-palette", placeholder("Command palette", "Search commands with Cmd/Ctrl-P."));

        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();
        ControlledWorkspaceShell shell = new ControlledWorkspaceShell(
                catalog, catalog.workspace("map"), panels);
        shell.setTop(controller.getGrabBar());
        return shell;
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
