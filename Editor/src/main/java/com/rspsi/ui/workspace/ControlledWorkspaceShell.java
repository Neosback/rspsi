package com.rspsi.ui.workspace;

import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.editor.ui.PanelDescriptor;
import com.rspsi.editor.ui.PanelPlacement;
import com.rspsi.editor.ui.WorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceDefinition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JavaFX frontend for the constrained workspace contracts.
 *
 * <p>The viewport remains the BorderPane center, tools and inspector remain
 * side rails, and bottom panels are tabs. There is no arbitrary docking or
 * renderer state hidden in this shell.</p>
 */
public final class ControlledWorkspaceShell extends BorderPane implements AutoCloseable {
    private static final double GAP = 8;
    private static final Insets PANEL_PADDING = new Insets(8);

    private final WorkspaceCatalog catalog;
    private final Map<String, Node> panels;
    private final ComboBox<String> workspacePicker = new ComboBox<>();
    private final MenuButton pluginCommands = new MenuButton("Plugin commands");
    private Node bottomTabs;
    private Node statusBar;
    private EditorPluginHost pluginHost;
    private String activeWorkspaceId;

    public ControlledWorkspaceShell(WorkspaceCatalog catalog,
                                    WorkspaceDefinition workspace,
                                    Map<String, Node> panels) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.panels = Map.copyOf(Objects.requireNonNull(panels, "panels"));
        getStyleClass().add("controlled-workspace-shell");
        var stylesheet = getClass().getResource("/css/workspace.css");
        if (stylesheet != null) getStylesheets().add(stylesheet.toExternalForm());
        setPadding(Insets.EMPTY);
        workspacePicker.getItems().setAll(catalog.workspaces().stream()
                .map(WorkspaceDefinition::id).toList());
        workspacePicker.setConverter(new StringConverter<>() {
            @Override public String toString(String value) {
                return value == null ? "" : title(value);
            }

            @Override public String fromString(String value) {
                return value;
            }
        });
        workspacePicker.setAccessibleText("Workspace preset");
        workspacePicker.setPromptText("Workspace");
        workspacePicker.setMinWidth(150);
        pluginCommands.setAccessibleText("Plugin commands");
        pluginCommands.setVisible(false);
        pluginCommands.setManaged(false);
        workspacePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(activeWorkspaceId)) show(newValue);
        });
        show(workspace);
    }

    public WorkspaceDefinition workspace(String id) {
        return catalog.workspace(id);
    }

    /** Returns a mounted panel so a frontend bridge can bind its data source. */
    public Node panelNode(String id) {
        return panels.get(id);
    }

    /** Returns the mounted persistent status row, if one was configured. */
    public Node statusBar() {
        return statusBar;
    }

    /** Routes a translated key event to the canonical viewport, if mounted. */
    public boolean dispatchKey(EditorKeyEvent event, boolean textInputFocused) {
        Objects.requireNonNull(event, "event");
        Node viewport = panelNode("viewport");
        return viewport instanceof ControlledViewportPanel controlled
                && controlled.canonicalViewport().dispatchKey(event, textInputFocused);
    }

    /**
     * Mounts one plugin host into all shell-owned contribution surfaces. The
     * shell, rather than an individual panel, owns the lifecycle.
     */
    public void bindPluginHost(EditorPluginHost host) {
        Objects.requireNonNull(host, "host");
        if (pluginHost != null && pluginHost != host) pluginHost.close();
        pluginHost = host;
        if (panelNode("tools") instanceof AdaptiveToolPanel tools) tools.bindPluginHost(host);
        if (panelNode("assets") instanceof AssetBrowserPanel assets) assets.bindPluginHost(host);
        if (panelNode("inspector") instanceof SessionInspectorPanel inspector) {
            inspector.bindPluginHost(host);
        }
        if (statusBar instanceof WorkspaceStatusBar status) status.bindPluginHost(host);
        if (panelNode("command-palette") instanceof PluginCommandPalettePanel palette) {
            palette.bindPluginHost(host);
        }
        if (panelNode("viewport") instanceof ControlledViewportPanel viewport) {
            viewport.canonicalViewport().bindPluginHost(host);
        }
        rebuildPluginCommands();
    }

    public void show(String workspaceId) {
        show(catalog.workspace(workspaceId));
    }

    public void show(WorkspaceDefinition workspace) {
        Objects.requireNonNull(workspace, "workspace");
        activeWorkspaceId = workspace.id();
        if (!workspace.id().equals(workspacePicker.getValue())) {
            workspacePicker.setValue(workspace.id());
        }
        detachMountedPanels();
        detach(statusBar);
        detach(bottomTabs);
        VBox left = rail("workspace-tools");
        VBox right = rail("workspace-inspector");
        TabPane bottom = new TabPane();
        bottom.getStyleClass().add("workspace-bottom-tabs");
        bottom.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        StackPane center = new StackPane();
        center.getStyleClass().add("workspace-viewport");

        List<PanelPlacement> placements = workspace.placements().stream()
                .sorted(Comparator.comparingInt(PanelPlacement::order)).toList();
        for (PanelPlacement placement : placements) {
            PanelDescriptor descriptor = catalog.panel(placement.panelId());
            Node panel = panel(placement.panelId(), descriptor);
            switch (placement.region()) {
                case LEFT -> left.getChildren().add(panel);
                case RIGHT -> right.getChildren().add(panel);
                case CENTER -> center.getChildren().add(panel);
                case OVERLAY -> center.getChildren().add(panel);
                case BOTTOM -> bottom.getTabs().add(new Tab(title(placement.panelId()), panel));
            }
        }
        setLeft(left.getChildren().isEmpty() ? null : left);
        setRight(right.getChildren().isEmpty() ? null : right);
        bottomTabs = bottom.getTabs().isEmpty() ? null : bottom;
        rebuildBottom();
        setCenter(center);
    }

    /** Mounts the legacy menu/grab bar beside the constrained workspace selector. */
    public void setTopBar(Node top) {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("workspace-top-bar");
        detach(workspacePicker);
        if (top != null) {
            detach(top);
            HBox.setHgrow(top, Priority.ALWAYS);
            bar.getChildren().add(top);
        }
        detach(pluginCommands);
        bar.getChildren().add(pluginCommands);
        bar.getChildren().add(workspacePicker);
        super.setTop(bar);
    }

    /** Mounts the persistent state row below the controlled bottom panels. */
    public void setStatusBar(Node statusBar) {
        detach(this.statusBar);
        detach(statusBar);
        this.statusBar = statusBar;
        rebuildBottom();
    }

    private VBox rail(String styleClass) {
        VBox rail = new VBox(GAP);
        rail.setPadding(PANEL_PADDING);
        rail.getStyleClass().add(styleClass);
        return rail;
    }

    private Node panel(String id, PanelDescriptor descriptor) {
        Node panel = panels.getOrDefault(id, missingPanel(id));
        if (!panel.getStyleClass().contains("workspace-panel")) {
            panel.getStyleClass().add("workspace-panel");
        }
        panel.setAccessibleText(title(id));
        if (panel instanceof Region region) {
            region.setMinWidth(descriptor.minimumWidth());
            region.setMinHeight(descriptor.minimumHeight());
        }
        return panel;
    }

    private static Node missingPanel(String id) {
        Label label = new Label("Panel unavailable: " + id);
        label.getStyleClass().add("workspace-missing-panel");
        return label;
    }

    /** Removes nodes from the previous preset before reusing the fixed panels. */
    private void detachMountedPanels() {
        for (Node panel : panels.values()) {
            if (panel.getParent() instanceof Pane parent) {
                parent.getChildren().remove(panel);
            }
        }
    }

    private static void detach(Node node) {
        if (node != null && node.getParent() instanceof Pane parent) {
            parent.getChildren().remove(node);
        }
    }

    private void rebuildBottom() {
        if (bottomTabs == null && statusBar == null) {
            setBottom(null);
            return;
        }
        if (bottomTabs == null) {
            setBottom(statusBar);
            return;
        }
        if (statusBar == null) {
            setBottom(bottomTabs);
            return;
        }
        VBox container = new VBox();
        container.getStyleClass().add("workspace-bottom-area");
        VBox.setVgrow(bottomTabs, javafx.scene.layout.Priority.ALWAYS);
        container.getChildren().addAll(bottomTabs, statusBar);
        setBottom(container);
    }

    private void rebuildPluginCommands() {
        pluginCommands.getItems().clear();
        if (pluginHost == null) {
            pluginCommands.setVisible(false);
            pluginCommands.setManaged(false);
            return;
        }
        for (var registration : pluginHost.registry().menuRegistrations()) {
            MenuItem item = new MenuItem(registration.label());
            item.setDisable(!pluginHost.context().session().canEdit());
            item.setOnAction(event -> pluginHost.context().session().execute(
                    pluginHost.registry().createCommand(registration.commandId())));
            pluginCommands.getItems().add(item);
        }
        boolean available = !pluginCommands.getItems().isEmpty();
        pluginCommands.setVisible(available);
        pluginCommands.setManaged(available);
    }

    private static String title(String id) {
        String[] words = id.split("-");
        return java.util.Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(id);
    }

    @Override
    public void close() {
        EditorPluginHost mounted = pluginHost;
        pluginHost = null;
        if (panelNode("viewport") instanceof ControlledViewportPanel viewport) {
            viewport.canonicalViewport().clearPluginHost();
        }
        if (mounted != null) mounted.close();
        rebuildPluginCommands();
    }
}
