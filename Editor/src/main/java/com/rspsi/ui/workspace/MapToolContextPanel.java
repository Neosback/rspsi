package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.options.Options;
import com.rspsi.core.misc.ToolType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Hosts the existing FXML tool controls below the viewport. No tool state is
 * stored here; changing a control still goes through MainController/Options.
 */
public final class MapToolContextPanel extends VBox {
    private final Label title = new Label("Select / Transform");
    private final VBox legacyOptions = new VBox();

    public MapToolContextPanel(MainController controller, Node legacyViewportToolBar) {
        Objects.requireNonNull(controller, "controller");
        setSpacing(4);
        setPadding(new Insets(4, 8, 4, 8));
        getStyleClass().add("workspace-tool-context-panel");
        title.getStyleClass().add("workspace-context-tool");

        HBox heading = new HBox(8, new Label("TOOL OPTIONS"), title);
        heading.getStyleClass().add("workspace-tool-context-heading");
        getChildren().add(heading);

        if (legacyViewportToolBar != null) {
            legacyViewportToolBar.getStyleClass().add("workspace-rehosted-control-bar");
            getChildren().add(legacyViewportToolBar);
        }

        FlowPane secondary = new FlowPane(6, 4);
        secondary.getStyleClass().add("workspace-secondary-tools");
        Node[] subtools = {
                controller.getDeleteObjectBtn(),
                controller.getPaintUnderlayBtn(), controller.getPaintFillBtn(),
                controller.getHeightFillBtn(), controller.getMoveObjectBtn(),
                controller.getSetFlagBtn()};
        for (Node node : subtools) {
            if (node == null) continue;
            removeFromParent(node);
            secondary.getChildren().add(node);
        }
        TitledPane more = new TitledPane("Subtools and flags", secondary);
        more.setExpanded(false);
        more.setAnimated(false);
        more.setAccessibleText("Additional map editing tools");
        getChildren().add(more);

        Options.currentTool.addListener((observable, oldValue, newValue) -> updateTitle(newValue));
        updateTitle(Options.currentTool.get());
    }

    private void updateTitle(ToolType type) {
        title.setText(switch (type) {
            case MODIFY_HEIGHT -> "Height";
            case PAINT_OVERLAY, PAINT_UNDERLAY -> "Paint / Terrain";
            case SELECT_OBJECT, SPAWN_OBJECT, DELETE_OBJECT -> "Objects";
            case SET_FLAGS -> "Flags and diagnostics";
            case IMPORT_SELECTION -> "Stamp / Fragment";
            default -> "Select / Transform";
        });
    }

    private static void removeFromParent(Node node) {
        if (node.getParent() instanceof javafx.scene.layout.Pane pane) pane.getChildren().remove(node);
    }
}
