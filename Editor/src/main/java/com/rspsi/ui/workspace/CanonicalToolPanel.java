package com.rspsi.ui.workspace;

import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.AttributeSelectionTool;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.LassoSelectTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateObjectTool;
import com.rspsi.editor.tool.RotateSelectionTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import com.rspsi.editor.debug.DebugOverlayMode;
import com.rspsi.editor.debug.DebugOverlaySettings;
import com.rspsi.editor.collision.RoutePreviewMode;
import com.rspsi.editor.assets.AssetDescriptor;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

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
    private final List<Button> actionButtons = new ArrayList<>();
    private final Map<CheckBox, DebugOverlayMode> debugModes = new LinkedHashMap<>();
    private final Label status = new Label("Select a tool");
    private final TextField underlay = field("Underlay", "1");
    private final TextField overlay = field("Overlay", "1");
    private final TextField height = field("Height", "8");
    private final TextField heightRadius = field("Height radius", "0");
    private final TextField flatten = field("Flatten", "0");
    private final TextField flags = field("Flags", "0");
    private final TextField rampStart = field("Ramp start", "0");
    private final TextField rampEnd = field("Ramp end", "64");
    private final TextField objectId = field("Object ID", "0");
    private final TextField objectType = field("Object type", "10");
    private final TextField objectRotation = field("Object rotation", "0");
    private final TextField snapGridSize = field("Snap grid", "1");
    private final TextField selectionQuarterTurns = field("Selection quarter turns", "1");
    private final TextField replacementId = field("Replacement ID", "0");
    private final TextField startX = field("Start X", "0");
    private final TextField startY = field("Start Y", "0");
    private final TextField targetX = field("Target X", "1");
    private final TextField targetY = field("Target Y", "0");
    private final ComboBox<ChangeHeightTool.Falloff> heightFalloff = new ComboBox<>();
    private final Label previewStatus = new Label("No preview");
    private final Label fragmentStatus = new Label("Select tiles, then copy");
    private CanonicalSceneViewport viewport;

    public CanonicalToolPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        setMinWidth(148);
        getStyleClass().addAll("workspace-panel", "canonical-tool-panel");
        setAccessibleText("OSRS editing tools");
        heightFalloff.getItems().setAll(ChangeHeightTool.Falloff.values());
        heightFalloff.getSelectionModel().select(ChangeHeightTool.Falloff.NONE);
        heightFalloff.setAccessibleText("Height brush falloff");
        heightFalloff.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("OSRS Tools");
        title.getStyleClass().add("workspace-panel-title");
        status.getStyleClass().add("workspace-panel-status");

        VBox terrain = section("Terrain");
        addTool(terrain, "Select", () -> null, true);
        addTool(terrain, "Paint underlay", () -> new PaintUnderlayTool(parse(underlay, "underlay")), false);
        addTool(terrain, "Paint overlay", () -> new PaintOverlayTool(parse(overlay, "overlay")), false);
        addTool(terrain, "Raise", () -> changeHeightTool(parse(height, "height")), false);
        addTool(terrain, "Lower", () -> changeHeightTool(-parse(height, "height")), false);
        addTool(terrain, "Flatten", () -> new FlattenTerrainTool(parse(flatten, "flatten")), false);
        addTool(terrain, "Smooth", () -> new SmoothTerrainTool(50), false);

        VBox advancedTerrain = section("More terrain");
        addTool(advancedTerrain, "Ramp", () -> new RampTerrainTool(
                parse(rampStart, "ramp start"), parse(rampEnd, "ramp end")), false);
        addTool(advancedTerrain, "Paint flags", () -> new PaintFlagsTool(parse(flags, "flags")), false);
        TitledPane advancedTerrainPane = collapsed("More terrain tools", advancedTerrain);

        VBox objects = section("Objects");
        addTool(objects, "Place object", () -> new PlaceObjectTool(
                parse(objectId, "object ID"), parse(objectType, "object type"),
                parse(objectRotation, "object rotation")), false);
        addTool(objects, "Move object", this::moveObjectTool, false);
        addTool(objects, "Rotate object", RotateObjectTool::new, false);
        addTool(objects, "Duplicate object", this::duplicateObjectTool, false);
        addTool(objects, "Delete object", DeleteObjectTool::new, false);

        VBox selection = section("Selection / transforms");
        addTool(selection, "Box select", BoxSelectTool::new, false);
        addTool(selection, "Lasso select", LassoSelectTool::new, false);
        addTool(selection, "Select by attribute", AttributeSelectionTool::new, false);
        addTool(selection, "Move selection", this::moveSelectionTool, false);
        addTool(selection, "Rotate selection", this::rotateSelectionTool, false);
        addTool(selection, "Duplicate selection", this::duplicateSelectionTool, false);
        addTool(selection, "Replace selection", () -> new ReplaceSelectionTool(
                parse(replacementId, "replacement ID")), false);
        TitledPane selectionPane = collapsed("Selection tools", selection);

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
        actionButtons.add(clear);
        clear.setOnAction(event -> {
            if (viewport != null) viewport.clearRoutePreview();
            previewStatus.setText("No preview");
        });
        previewButtons.getChildren().addAll(route, los, reach, clear);
        previewStatus.getStyleClass().add("workspace-panel-status");
        previewStatus.setWrapText(true);
        preview.getChildren().addAll(previewFields, previewButtons, previewStatus);

        VBox fragments = section("World fragment");
        HBox fragmentButtons = new HBox(4);
        Button copy = new Button("Copy");
        copy.setAccessibleText("Copy selected world fragment");
        actionButtons.add(copy);
        copy.setOnAction(event -> fragmentStatus("Copied", viewport == null
                ? "No viewport" : viewport.copySelectionToClipboard()));
        Button paste = new Button("Paste");
        paste.setAccessibleText("Paste world fragment at target coordinates");
        actionButtons.add(paste);
        paste.setOnAction(event -> {
            if (viewport == null) {
                fragmentStatus("Paste", "No viewport");
                return;
            }
            try {
                fragmentStatus("Paste", viewport.pasteFragmentFromClipboard(
                        parse(targetX, "target X"), parse(targetY, "target Y")));
            } catch (IllegalArgumentException exception) {
                fragmentStatus("Paste", exception.getMessage());
            }
        });
        Button export = new Button("Export");
        export.setAccessibleText("Export selected world fragment to a file");
        actionButtons.add(export);
        export.setOnAction(event -> chooseExportFile());
        Button importButton = new Button("Import");
        importButton.setAccessibleText("Import a world fragment file at target coordinates");
        actionButtons.add(importButton);
        importButton.setOnAction(event -> chooseImportFile());
        fragmentButtons.getChildren().addAll(copy, paste, export, importButton);
        fragmentStatus.getStyleClass().add("workspace-panel-status");
        fragmentStatus.setWrapText(true);
        fragments.getChildren().addAll(fragmentButtons, fragmentStatus);

        GridPane settings = new GridPane();
        settings.setHgap(6);
        settings.setVgap(5);
        addSetting(settings, 0, "Underlay", underlay);
        addSetting(settings, 1, "Overlay", overlay);
        addSetting(settings, 2, "Height", height);
        addSetting(settings, 3, "Height radius", heightRadius);
        addSetting(settings, 4, "Height falloff", heightFalloff);
        addSetting(settings, 5, "Flatten", flatten);
        addSetting(settings, 6, "Flags", flags);
        addSetting(settings, 7, "Ramp start", rampStart);
        addSetting(settings, 8, "Ramp end", rampEnd);
        addSetting(settings, 9, "Object ID", objectId);
        addSetting(settings, 10, "Object type", objectType);
        addSetting(settings, 11, "Object rotation", objectRotation);
        addSetting(settings, 12, "Snap grid", snapGridSize);
        addSetting(settings, 13, "Selection turns", selectionQuarterTurns);
        addSetting(settings, 14, "Replacement ID", replacementId);
        getChildren().addAll(title, status, terrain, advancedTerrainPane, objects,
                selectionPane, debug, preview, fragments, settings);
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
        actionButtons.forEach(button -> button.setDisable(viewport == null));
    }

    private VBox section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("workspace-tool-section");
        VBox section = new VBox(4);
        section.getChildren().add(label);
        return section;
    }

    private static TitledPane collapsed(String title, VBox content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(false);
        pane.setAnimated(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setAccessibleText(title);
        pane.getStyleClass().add("workspace-collapsible-tools");
        return pane;
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

    private ChangeHeightTool changeHeightTool(int delta) {
        ChangeHeightTool tool = new ChangeHeightTool(delta);
        tool.setRadius(parse(heightRadius, "height radius"));
        tool.setFalloff(heightFalloff.getValue());
        return tool;
    }

    private RotateSelectionTool rotateSelectionTool() {
        RotateSelectionTool tool = new RotateSelectionTool();
        tool.setQuarterTurns(parse(selectionQuarterTurns, "selection quarter turns"));
        return tool;
    }

    private MoveObjectTool moveObjectTool() {
        MoveObjectTool tool = new MoveObjectTool();
        tool.setSnapGridSize(parse(snapGridSize, "snap grid"));
        return tool;
    }

    private DuplicateObjectTool duplicateObjectTool() {
        DuplicateObjectTool tool = new DuplicateObjectTool();
        tool.setSnapGridSize(parse(snapGridSize, "snap grid"));
        return tool;
    }

    private MoveSelectionTool moveSelectionTool() {
        MoveSelectionTool tool = new MoveSelectionTool();
        tool.setSnapGridSize(parse(snapGridSize, "snap grid"));
        return tool;
    }

    private DuplicateSelectionTool duplicateSelectionTool() {
        DuplicateSelectionTool tool = new DuplicateSelectionTool();
        tool.setSnapGridSize(parse(snapGridSize, "snap grid"));
        return tool;
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
        actionButtons.add(button);
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

    private void fragmentStatus(String action, String message) {
        fragmentStatus.setText(action + ": " + message);
    }

    private void chooseExportFile() {
        if (viewport == null || getScene() == null) {
            fragmentStatus("Export", "No viewport");
            return;
        }
        FileChooser chooser = fragmentChooser("Export world fragment");
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) fragmentStatus("Export", viewport.exportSelection(file.toPath()));
    }

    private void chooseImportFile() {
        if (viewport == null || getScene() == null) {
            fragmentStatus("Import", "No viewport");
            return;
        }
        FileChooser chooser = fragmentChooser("Import world fragment");
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try {
                fragmentStatus("Import", viewport.importFragment(file.toPath(),
                        parse(targetX, "target X"), parse(targetY, "target Y")));
            } catch (IllegalArgumentException exception) {
                fragmentStatus("Import", exception.getMessage());
            }
        }
    }

    private static FileChooser fragmentChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "RSPSi world fragment (*.json)", "*.json"));
        return chooser;
    }

    private static TextField field(String label, String value) {
        TextField field = new TextField(value);
        field.setAccessibleText(label + " value");
        field.setPrefColumnCount(5);
        return field;
    }

    private static void addSetting(GridPane grid, int row, String label, javafx.scene.control.Control field) {
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
        actionButtons.forEach(button -> button.setDisable(true));
    }
}
