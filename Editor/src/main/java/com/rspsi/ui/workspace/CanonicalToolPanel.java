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
import com.rspsi.editor.debug.DebugOverlayMode;
import com.rspsi.editor.debug.DebugOverlaySettings;
import com.rspsi.editor.collision.RoutePreviewMode;
import com.rspsi.editor.assets.AssetDescriptor;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final List<CheckBox> debugButtons = new ArrayList<>();
    private final Map<CheckBox, DebugOverlayMode> debugModes = new LinkedHashMap<>();
    private final Label status = new Label("Select a tool");
    private final TextField underlay = field("Underlay", "1");
    private final TextField overlay = field("Overlay", "1");
    private final TextField height = field("Height", "8");
    private final TextField flatten = field("Flatten", "0");
    private final TextField objectId = field("Object ID", "0");
    private final TextField objectType = field("Object type", "10");
    private final TextField startX = field("Start X", "0");
    private final TextField startY = field("Start Y", "0");
    private final TextField targetX = field("Target X", "1");
    private final TextField targetY = field("Target Y", "0");
    private final Label previewStatus = new Label("No preview");
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

        VBox debug = section("Debug overlays");
        addDebug(debug, "Tile grid", DebugOverlayMode.TILE_GRID);
        addDebug(debug, "Chunk grid", DebugOverlayMode.CHUNK_GRID);
        addDebug(debug, "Region grid", DebugOverlayMode.REGION_GRID);
        addDebug(debug, "Window boundary", DebugOverlayMode.LOADED_WORLD_WINDOW);
        addDebug(debug, "Tile flags", DebugOverlayMode.TILE_FLAGS);
        addDebug(debug, "Collision", DebugOverlayMode.COLLISION);
        addDebug(debug, "Bridge links", DebugOverlayMode.BRIDGE_LINKS);

        VBox preview = section("Route / LOS preview");
        GridPane previewFields = new GridPane();
        previewFields.setHgap(6);
        previewFields.setVgap(5);
        addSetting(previewFields, 0, "Start X", startX);
        addSetting(previewFields, 1, "Start Y", startY);
        addSetting(previewFields, 2, "Target X", targetX);
        addSetting(previewFields, 3, "Target Y", targetY);
        HBox previewButtons = new HBox(4);
        Button route = previewButton("Route", RoutePreviewMode.ROUTE);
        Button los = previewButton("LOS", RoutePreviewMode.LINE_OF_SIGHT);
        Button reach = previewButton("Reach", RoutePreviewMode.REACH);
        Button clear = new Button("Clear");
        clear.setAccessibleText("Clear route preview");
        clear.setOnAction(event -> {
            if (viewport != null) viewport.clearRoutePreview();
            previewStatus.setText("No preview");
        });
        previewButtons.getChildren().addAll(route, los, reach, clear);
        previewStatus.getStyleClass().add("workspace-panel-status");
        previewStatus.setWrapText(true);
        preview.getChildren().addAll(previewFields, previewButtons, previewStatus);

        GridPane settings = new GridPane();
        settings.setHgap(6);
        settings.setVgap(5);
        addSetting(settings, 0, "Underlay", underlay);
        addSetting(settings, 1, "Overlay", overlay);
        addSetting(settings, 2, "Height", height);
        addSetting(settings, 3, "Flatten", flatten);
        addSetting(settings, 4, "Object ID", objectId);
        addSetting(settings, 5, "Object type", objectType);
        getChildren().addAll(title, status, terrain, objects, debug, preview, settings);
        setViewport(null);
    }

    public void bind(CanonicalSceneViewport viewport) {
        setViewport(Objects.requireNonNull(viewport, "viewport"));
        status.setText("Select a tool");
    }

    /** Configures the place-object tool from a neutral asset-browser result. */
    public void setObjectAsset(AssetDescriptor asset) {
        Objects.requireNonNull(asset, "asset");
        if (!"object".equalsIgnoreCase(asset.type())) return;
        objectId.setText(Integer.toString(asset.id()));
        status.setText("Object selected: " + asset.name() + " (" + asset.id() + ")");
    }

    private void setViewport(CanonicalSceneViewport viewport) {
        this.viewport = viewport;
        toolButtons.forEach(button -> button.setDisable(viewport == null));
        debugButtons.forEach(button -> button.setDisable(viewport == null));
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

    private void addDebug(VBox section, String label, DebugOverlayMode mode) {
        CheckBox check = new CheckBox(label);
        check.setAccessibleText("Toggle " + label + " overlay");
        check.setOnAction(event -> updateDebugSettings());
        debugButtons.add(check);
        debugModes.put(check, mode);
        section.getChildren().add(check);
    }

    private void updateDebugSettings() {
        if (viewport == null) return;
        java.util.EnumSet<DebugOverlayMode> modes = java.util.EnumSet.noneOf(DebugOverlayMode.class);
        for (Map.Entry<CheckBox, DebugOverlayMode> entry : debugModes.entrySet()) {
            if (entry.getKey().isSelected()) modes.add(entry.getValue());
        }
        viewport.setDebugOverlaySettings(new DebugOverlaySettings(modes));
    }

    private Button previewButton(String label, RoutePreviewMode mode) {
        Button button = new Button(label);
        button.setAccessibleText("Preview " + label);
        button.setMinHeight(30);
        button.setOnAction(event -> {
            if (viewport == null) return;
            try {
                var result = viewport.previewRoute(mode, parse(startX, "start X"),
                        parse(startY, "start Y"), parse(targetX, "target X"),
                        parse(targetY, "target Y"), 1, false);
                previewStatus.setText(result.message());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                previewStatus.setText(exception.getMessage());
            }
        });
        return button;
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
        debugButtons.forEach(button -> {
            button.setSelected(false);
            button.setDisable(true);
        });
    }
}
