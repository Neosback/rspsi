package com.rspsi.ui.workspace;

import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import com.rspsi.options.Options;
import com.rspsi.core.misc.ToolType;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Compact viewport context row; feature tools can add controls later. */
public final class ContextToolbar extends HBox {
    private final Label activeTool = new Label("Select / Transform");

    public ContextToolbar() {
        setSpacing(6);
        setPadding(new Insets(6, 8, 6, 8));
        getStyleClass().add("workspace-context-toolbar");
        setAccessibleText("Active tool options");

        Label title = new Label("MAP EDITOR");
        title.getStyleClass().add("workspace-context-title");
        activeTool.getStyleClass().add("workspace-context-tool");

        getChildren().addAll(title, activeTool);
        Options.currentTool.addListener((observable, oldValue, newValue) ->
                setActiveTool(labelFor(newValue)));
        setActiveTool(labelFor(Options.currentTool.get()));
    }

    public void setActiveTool(String label) {
        activeTool.setText(label == null || label.isBlank() ? "Select / Transform" : label);
    }

    private static Label separator() {
        Label separator = new Label("•");
        separator.getStyleClass().add("workspace-context-separator");
        return separator;
    }

    private static String labelFor(ToolType type) {
        if (type == null) return "Select / Transform";
        return switch (type) {
            case MODIFY_HEIGHT -> "Height";
            case PAINT_OVERLAY, PAINT_UNDERLAY -> "Paint / Terrain";
            case SELECT_OBJECT, SPAWN_OBJECT, DELETE_OBJECT -> "Objects";
            case SET_FLAGS -> "Flags and diagnostics";
            case IMPORT_SELECTION -> "Stamp / Fragment";
            default -> "Select / Transform";
        };
    }
}
