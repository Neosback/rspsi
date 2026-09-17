package com.rspsi.ui.workspace;

import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Small OSRS tool rail for the canonical viewport. It owns only JavaFX
 * controls; the selected tools and their edits remain neutral tool/session
 * objects owned by the viewport and editor core.
 */
public final class CanonicalToolPanel extends VBox implements AutoCloseable {
    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> toolButtons = new ArrayList<>();
    private final Label status = new Label("Select a tool");
    private final TextField underlay = field("Underlay", "1");
    private final TextField overlay = field("Overlay", "1");
    private final TextField height = field("Height", "8");
    private final TextField flatten = field("Flatten", "0");
    private final TextField objectId = field("Object ID", "0");
    private final TextField objectType = field("Object type", "10");
    private CanonicalSceneViewport viewport;

    public CanonicalToolPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        setMinWidth(148);
        getStyleClass().addAll("workspace-panel", "canonical-tool-panel");
        setAccessibleText("OSRS editing tools");

        Label title = new Label("OSRS Tools");
        title.getStyleClass().add("workspace-panel-title");
        status.getStyleClass().add("workspace-panel-status");

        VBox terrain = section("Terrain");
        addTool(terrain, "Select", () -> null, true);
        addTool(terrain, "Paint underlay", () -> new PaintUnderlayTool(parse(underlay, "underlay")), false);
        addTool(terrain, "Paint overlay", () -> new PaintOverlayTool(parse(overlay, "overlay")), false);
        addTool(terrain, "Raise", () -> new ChangeHeightTool(parse(height, "height")), false);
        addTool(terrain, "Lower", () -> new ChangeHeightTool(-parse(height, "height")), false);
        addTool(terrain, "Flatten", () -> new FlattenTerrainTool(parse(flatten, "flatten")), false);
        addTool(terrain, "Smooth", () -> new SmoothTerrainTool(50), false);

        VBox objects = section("Objects");
        addTool(objects, "Place object", () -> new PlaceObjectTool(
                parse(objectId, "object ID"), parse(objectType, "object type"), 0), false);
        addTool(objects, "Move object", MoveObjectTool::new, false);
        addTool(objects, "Rotate object", RotateObjectTool::new, false);
        addTool(objects, "Duplicate object", DuplicateObjectTool::new, false);
        addTool(objects, "Delete object", DeleteObjectTool::new, false);

        GridPane settings = new GridPane();
        settings.setHgap(6);
        settings.setVgap(5);
        addSetting(settings, 0, "Underlay", underlay);
        addSetting(settings, 1, "Overlay", overlay);
        addSetting(settings, 2, "Height", height);
        addSetting(settings, 3, "Flatten", flatten);
        addSetting(settings, 4, "Object ID", objectId);
        addSetting(settings, 5, "Object type", objectType);
        getChildren().addAll(title, status, terrain, objects, settings);
        setViewport(null);
    }

    public void bind(CanonicalSceneViewport viewport) {
        setViewport(Objects.requireNonNull(viewport, "viewport"));
        status.setText("Select a tool");
    }

    private void setViewport(CanonicalSceneViewport viewport) {
        this.viewport = viewport;
        toolButtons.forEach(button -> button.setDisable(viewport == null));
    }

    private VBox section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("workspace-tool-section");
        VBox section = new VBox(4);
        section.getChildren().add(label);
        return section;
    }

    private void addTool(VBox section, String label, Supplier<EditorTool> factory, boolean select) {
        ToggleButton button = new ToggleButton(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(30);
        button.setAccessibleText(label);
        button.setToggleGroup(group);
        toolButtons.add(button);
        VBox.setVgrow(button, Priority.NEVER);
        button.setOnAction(event -> {
            if (viewport == null) return;
            if (select) {
                viewport.deactivateTool();
                status.setText("Selection");
                return;
            }
            try {
                viewport.activateTool(factory.get());
                status.setText(label);
            } catch (IllegalArgumentException exception) {
                group.selectToggle(null);
                status.setText(exception.getMessage());
            }
        });
        section.getChildren().add(button);
    }

    private static TextField field(String label, String value) {
        TextField field = new TextField(value);
        field.setAccessibleText(label + " value");
        field.setPrefColumnCount(5);
        return field;
    }

    private static void addSetting(GridPane grid, int row, String label, TextField field) {
        Label text = new Label(label);
        text.setLabelFor(field);
        text.getStyleClass().add("workspace-property-key");
        grid.addRow(row, text, field);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private static int parse(TextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    @Override
    public void close() {
        if (viewport != null) viewport.deactivateTool();
        viewport = null;
        group.selectToggle(null);
        toolButtons.forEach(button -> button.setDisable(true));
    }
}
