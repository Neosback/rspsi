package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import com.rspsi.options.Options;
import com.rspsi.core.misc.ToolType;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.Objects;

/** Persistent selection/action strip below the active tool options. */
public final class SelectorStrip extends HBox {
    public SelectorStrip(MainController controller) {
        Objects.requireNonNull(controller, "controller");
        setSpacing(5);
        setPadding(new Insets(4, 8, 4, 8));
        getStyleClass().add("workspace-selector-strip");
        setAccessibleText("Selection and action modes");

        ToggleGroup selectors = new ToggleGroup();
        ToggleButton single = selector("Single tile", selectors);
        single.setSelected(true);
        ToggleButton rectangle = selector("Rectangle / multi-tile selection", selectors);
        ToggleButton object = selector("Object selection", selectors);
        Button paint = action("Paint", "Paint with the current terrain tool");
        Button height = action("Height", "Modify tile heights");

        single.setOnAction(event -> fire(controller.getSelectTileBtn()));
        rectangle.setOnAction(event -> fire(controller.getSelectTileBtn()));
        object.setOnAction(event -> fire(controller.getSelectObjectBtn()));
        paint.setOnAction(event -> fire(controller.getPaintOverlayBtn()));
        height.setOnAction(event -> fire(controller.getHeightModifyBtn()));

        getChildren().addAll(label("Selector"), single, rectangle, object,
                separator(), paint, height);
        if (controller.getToolGroup() != null) {
            Options.currentTool.addListener((obs, oldValue, newValue) -> {
                if (newValue == ToolType.SELECT_OBJECT) object.setSelected(true);
                else if (newValue == ToolType.SELECT_TILE) single.setSelected(true);
            });
        }
    }

    private static ToggleButton selector(String text, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setTooltip(new Tooltip(text));
        button.setAccessibleText(text);
        return button;
    }

    private static Button action(String text, String tooltip) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }

    private static javafx.scene.control.Label label(String text) {
        javafx.scene.control.Label label = new javafx.scene.control.Label(text);
        label.getStyleClass().add("workspace-selector-label");
        return label;
    }

    private static javafx.scene.control.Label separator() {
        javafx.scene.control.Label label = new javafx.scene.control.Label("•");
        label.getStyleClass().add("workspace-context-separator");
        return label;
    }

    private static void fire(javafx.scene.control.ButtonBase button) {
        if (button != null && !button.isDisabled()) button.fire();
    }
}
