package com.rspsi.ui.workspace;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.editor.ui.PanelDescriptor;
import com.rspsi.editor.ui.PanelPlacement;
import com.rspsi.editor.ui.WorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceDefinition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
public final class ControlledWorkspaceShell extends BorderPane {
    private static final double GAP = 8;
    private static final Insets PANEL_PADDING = new Insets(8);

    private final WorkspaceCatalog catalog;
    private final Map<String, Node> panels;

    public ControlledWorkspaceShell(WorkspaceCatalog catalog,
                                    WorkspaceDefinition workspace,
                                    Map<String, Node> panels) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.panels = Map.copyOf(Objects.requireNonNull(panels, "panels"));
        getStyleClass().add("controlled-workspace-shell");
        var stylesheet = getClass().getResource("/css/workspace.css");
        if (stylesheet != null) getStylesheets().add(stylesheet.toExternalForm());
        setPadding(Insets.EMPTY);
        show(workspace);
    }

    public WorkspaceDefinition workspace(String id) {
        return catalog.workspace(id);
    }

    /** Returns a mounted panel so a frontend bridge can bind its data source. */
    public Node panelNode(String id) {
        return panels.get(id);
    }

    public void show(String workspaceId) {
        show(catalog.workspace(workspaceId));
    }

    public void show(WorkspaceDefinition workspace) {
        Objects.requireNonNull(workspace, "workspace");
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
        setBottom(bottom.getTabs().isEmpty() ? null : bottom);
        setCenter(center);
    }

    private VBox rail(String styleClass) {
        VBox rail = new VBox(GAP);
        rail.setPadding(PANEL_PADDING);
        rail.getStyleClass().add(styleClass);
        return rail;
    }

    private Node panel(String id, PanelDescriptor descriptor) {
        Node panel = panels.getOrDefault(id, missingPanel(id));
        panel.getStyleClass().add("workspace-panel");
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

    private static String title(String id) {
        String[] words = id.split("-");
        return java.util.Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(id);
    }
}
