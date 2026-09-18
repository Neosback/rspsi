package com.rspsi.ui.workspace;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

/** Read-only scene hierarchy surface for the first shell migration. */
public final class WorldOutlinerPanel extends VBox {
    private final TreeView<String> tree = new TreeView<>();

    public WorldOutlinerPanel() {
        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().addAll("workspace-panel", "workspace-outliner");
        setAccessibleText("World outliner");

        Label title = new Label("WORLD OUTLINER");
        title.getStyleClass().add("workspace-panel-title");
        tree.setShowRoot(false);
        tree.setRoot(root());
        tree.setCellFactory(view -> new javafx.scene.control.TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAccessibleText(empty ? null : item);
            }
        });
        VBox.setVgrow(tree, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(title, tree);
    }

    private static TreeItem<String> root() {
        TreeItem<String> root = new TreeItem<>("Scene");
        root.getChildren().addAll(
                branch("Loaded regions", "Region window"),
                branch("Planes", "Plane 0", "Plane 1", "Plane 2", "Plane 3"),
                branch("Terrain", "Underlays", "Overlays", "Height vertices"),
                branch("Objects", "Walls", "Decorations", "Game objects", "Ground decorations"),
                branch("Diagnostics", "Collision", "Bridges", "Occluders"));
        return root;
    }

    private static TreeItem<String> branch(String title, String... children) {
        TreeItem<String> branch = new TreeItem<>(title);
        for (String child : children) branch.getChildren().add(new TreeItem<>(child));
        branch.setExpanded("Loaded regions".equals(title) || "Planes".equals(title));
        return branch;
    }
}
