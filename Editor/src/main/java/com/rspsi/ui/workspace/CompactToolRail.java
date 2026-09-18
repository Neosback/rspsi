package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.options.Options;
import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * The five primary map workflows. The toggle buttons are the original FXML
 * controls, so MainController listeners and Options bindings remain the source
 * of truth; this rail only changes where those controls are presented.
 */
public final class CompactToolRail extends VBox {
    public CompactToolRail(MainController controller) {
        Objects.requireNonNull(controller, "controller");
        setSpacing(7);
        setAlignment(Pos.TOP_CENTER);
        getStyleClass().add("workspace-tool-rail");
        setAccessibleText("Map editing tools");

        getChildren().addAll(
                primary(controller.getSelectTileBtn(), "Select", "1", StudioIcon.SELECT),
                primary(controller.getPaintOverlayBtn(), "Paint / Terrain", "2", StudioIcon.TERRAIN),
                primary(controller.getHeightModifyBtn(), "Height", "3", StudioIcon.HEIGHT),
                primary(controller.getSelectObjectBtn(), "Objects", "4", StudioIcon.OBJECT));

        getChildren().add(divider());
        Button fragment = new Button();
        fragment.setGraphic(StudioIconFactory.icon(StudioIcon.FRAGMENT));
        fragment.setTooltip(new Tooltip("Stamp / Fragment (5)"));
        fragment.setAccessibleText("Stamp or fragment tool, shortcut 5");
        fragment.setOnAction(event -> {
            if (controller.getPasteTilesBtn() != null) controller.getPasteTilesBtn().fire();
        });
        getChildren().add(badged(fragment, "5"));

        Label hint = new Label("1–5");
        hint.getStyleClass().add("workspace-tool-rail-hint");
        getChildren().add(hint);
    }

    private static StackPane primary(ToggleButton button, String label, String badge, StudioIcon icon) {
        removeFromParent(button);
        button.setText("");
        button.setGraphic(StudioIconFactory.icon(icon));
        button.setTooltip(new Tooltip(label + " (" + badge + ")"));
        button.setAccessibleText(label + ", shortcut " + badge);
        button.getStyleClass().add("workspace-primary-tool");
        return badged(button, badge);
    }

    private static StackPane badged(Node node, String badge) {
        StackPane holder = new StackPane(node);
        holder.getStyleClass().add("workspace-tool-button-holder");
        Label number = new Label(badge);
        number.getStyleClass().add("workspace-tool-badge");
        StackPane.setAlignment(number, Pos.BOTTOM_RIGHT);
        holder.getChildren().add(number);
        return holder;
    }

    private static HBox divider() {
        HBox divider = new HBox();
        divider.getStyleClass().add("workspace-tool-divider");
        return divider;
    }

    private static void removeFromParent(Node node) {
        if (node.getParent() instanceof javafx.scene.layout.Pane pane) {
            pane.getChildren().remove(node);
        }
    }
}
