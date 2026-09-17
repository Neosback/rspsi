package com.rspsi.ui.workspace;

import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.plugin.EditorToolRegistration;
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
import javafx.css.PseudoClass;
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
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> toolButtons = new ArrayList<>();
    private final List<CheckBox> debugButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private final Map<CheckBox, DebugOverlayMode> debugModes = new LinkedHashMap<>();
    private final List<ToggleButton> pluginButtons = new ArrayList<>();
    private final Map<String, VBox> toolSections = new LinkedHashMap<>();
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
    private final TextField objectQuarterTurns = field("Object quarter turns", "1");
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
    private EditorPluginHost pluginHost;
    private final VBox pluginSection;
    private final VBox pluginContextSection;
    private final GridPane legacySettings;

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

        VBox advancedTerrain = section("More terrain");
        TitledPane advancedTerrainPane = collapsed("More terrain tools", advancedTerrain);

        VBox objects = section("Objects");

        VBox selection = section("Selection / transforms");
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

        legacySettings = new GridPane();
        legacySettings.setHgap(6);
        legacySettings.setVgap(5);
        addSetting(legacySettings, 0, "Underlay", underlay);
        addSetting(legacySettings, 1, "Overlay", overlay);
        addSetting(legacySettings, 2, "Height", height);
        addSetting(legacySettings, 3, "Height radius", heightRadius);
        addSetting(legacySettings, 4, "Height falloff", heightFalloff);
        addSetting(legacySettings, 5, "Flatten", flatten);
        addSetting(legacySettings, 6, "Flags", flags);
        addSetting(legacySettings, 7, "Ramp start", rampStart);
        addSetting(legacySettings, 8, "Ramp end", rampEnd);
        addSetting(legacySettings, 9, "Object ID", objectId);
        addSetting(legacySettings, 10, "Object type", objectType);
        addSetting(legacySettings, 11, "Object rotation", objectRotation);
        addSetting(legacySettings, 12, "Object turns", objectQuarterTurns);
        addSetting(legacySettings, 13, "Snap grid", snapGridSize);
        addSetting(legacySettings, 14, "Selection turns", selectionQuarterTurns);
        addSetting(legacySettings, 15, "Replacement ID", replacementId);
        pluginSection = section("Plugin tools");
        pluginSection.setVisible(false);
        pluginSection.setManaged(false);
        pluginContextSection = section("Plugin context");
        pluginContextSection.setVisible(false);
        pluginContextSection.setManaged(false);
        getChildren().addAll(title, status, terrain, advancedTerrainPane, objects,
                selectionPane, debug, preview, fragments, legacySettings, pluginSection,
                pluginContextSection);
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

    /** Mounts neutral tool contributions discovered by the plugin host. */
    public void bindPluginHost(EditorPluginHost host) {
        Objects.requireNonNull(host, "host");
        pluginHost = host;
        legacySettings.setVisible(false);
        legacySettings.setManaged(false);
        clearPluginContext();
        pluginButtons.forEach(button -> {
            if (button.getParent() instanceof VBox parent) {
                parent.getChildren().remove(button);
            }
            toolButtons.remove(button);
            group.getToggles().remove(button);
        });
        pluginButtons.clear();
        pluginSection.getChildren().clear();
        Label label = new Label("Plugin tools");
        label.getStyleClass().add("workspace-tool-section");
        pluginSection.getChildren().add(label);
        for (EditorToolRegistration registration : host.registry().toolRegistrations()) {
            int before = toolButtons.size();
            VBox section = toolSections.getOrDefault(registration.category(), pluginSection);
            addTool(section, registration.label(),
                    () -> configuredTool(host, registration.id()), false,
                    () -> renderPluginContext(host, registration.id()));
            pluginButtons.add(toolButtons.get(before));
        }
        boolean available = pluginSection.getChildren().size() > 1;
        pluginSection.setVisible(available);
        pluginSection.setManaged(available);
    }

    private void setViewport(CanonicalSceneViewport viewport) {
        this.viewport = viewport;
        toolButtons.forEach(button -> button.setDisable(viewport == null));
        debugButtons.forEach(button -> button.setDisable(viewport == null));
        actionButtons.forEach(button -> button.setDisable(viewport == null));
        pluginContextSection.setDisable(viewport == null);
    }

    private VBox section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("workspace-tool-section");
        VBox section = new VBox(4);
        section.getChildren().add(label);
        toolSections.put(title, section);
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
        addTool(section, label, factory, select, null);
    }

    private void addTool(VBox section, String label, Supplier<EditorTool> factory,
                         boolean select, Runnable onSelected) {
        ToggleButton button = new ToggleButton(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(30);
        button.setAccessibleText(label);
        button.setToggleGroup(group);
        toolButtons.add(button);
        VBox.setVgrow(button, Priority.NEVER);
        button.setOnAction(event -> {
            if (viewport == null) return;
            clearPluginContext();
            if (select) {
                viewport.deactivateTool();
                status.setText("Selection");
                return;
            }
            try {
                viewport.activateTool(factory.get());
                status.setText(label);
                if (onSelected != null) onSelected.run();
            } catch (IllegalArgumentException exception) {
                group.selectToggle(null);
                status.setText(exception.getMessage());
            }
        });
        section.getChildren().add(button);
    }

    private void renderPluginContext(EditorPluginHost host, String toolId) {
        if (host != pluginHost || viewport == null) return;
        List<EditorSetting> settings = host.registry().settingsForTool(host.context(), toolId);
        if (settings.isEmpty()) return;

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(5);
        for (int index = 0; index < settings.size(); index++) {
            EditorSetting setting = settings.get(index);
            addSetting(grid, index, setting.label(), pluginSettingControl(setting));
        }
        pluginContextSection.getChildren().add(grid);
        pluginContextSection.setVisible(true);
        pluginContextSection.setManaged(true);
    }

    private javafx.scene.control.Control pluginSettingControl(EditorSetting setting) {
        return switch (setting.type()) {
            case INTEGER -> numericSettingControl(setting, true);
            case DECIMAL -> numericSettingControl(setting, false);
            case BOOLEAN -> booleanSettingControl(setting);
            case ENUM -> enumSettingControl(setting);
        };
    }

    private TextField numericSettingControl(EditorSetting setting, boolean integer) {
        TextField field = field(setting.label(), String.valueOf(setting.value()));
        field.setAccessibleText(setting.label() + " plugin setting");
        Runnable commit = () -> {
            try {
                Object value = integer
                        ? Integer.parseInt(field.getText().trim())
                        : Double.parseDouble(field.getText().trim());
                setting.setValue(value);
                field.setText(String.valueOf(setting.value()));
                field.pseudoClassStateChanged(INVALID, false);
            } catch (IllegalArgumentException exception) {
                field.pseudoClassStateChanged(INVALID, true);
                status.setText(setting.label() + ": " + exception.getMessage());
            }
        };
        field.setOnAction(event -> commit.run());
        field.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) commit.run();
        });
        return field;
    }

    private CheckBox booleanSettingControl(EditorSetting setting) {
        CheckBox check = new CheckBox();
        check.setSelected(Boolean.TRUE.equals(setting.value()));
        check.setAccessibleText(setting.label() + " plugin setting");
        check.setOnAction(event -> {
            try {
                setting.setValue(check.isSelected());
            } catch (IllegalArgumentException exception) {
                status.setText(setting.label() + ": " + exception.getMessage());
            }
        });
        return check;
    }

    private ComboBox<String> enumSettingControl(EditorSetting setting) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(setting.options());
        combo.getSelectionModel().select(String.valueOf(setting.value()));
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setAccessibleText(setting.label() + " plugin setting");
        combo.setOnAction(event -> {
            String value = combo.getValue();
            if (value == null) return;
            try {
                setting.setValue(value);
            } catch (IllegalArgumentException exception) {
                status.setText(setting.label() + ": " + exception.getMessage());
            }
        });
        return combo;
    }

    private void clearPluginContext() {
        pluginContextSection.getChildren().clear();
        pluginContextSection.getChildren().add(sectionLabel("Plugin context"));
        pluginContextSection.setVisible(false);
        pluginContextSection.setManaged(false);
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("workspace-tool-section");
        return label;
    }

    private EditorTool configuredTool(EditorPluginHost host, String id) {
        return host.registry().createTool(id);
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
            int value = Integer.parseInt(field.getText().trim());
            field.pseudoClassStateChanged(INVALID, false);
            return value;
        } catch (NumberFormatException exception) {
            field.pseudoClassStateChanged(INVALID, true);
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    @Override
    public void close() {
        pluginHost = null;
        legacySettings.setVisible(true);
        legacySettings.setManaged(true);
        if (viewport != null) viewport.deactivateTool();
        clearPluginContext();
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
