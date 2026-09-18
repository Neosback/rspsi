package com.rspsi.ui.workspace;

import com.rspsi.options.Options;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Category-driven right panel, kept separate from the neutral inspector state. */
public final class RightSettingsPanel extends VBox {
    private final Label title = new Label();
    private final Label description = new Label();
    private final VBox content = new VBox(10);
    private final Map<String, Node> categories = new LinkedHashMap<>();

    public RightSettingsPanel(Node outliner, Node inspector) {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().addAll("workspace-panel", "workspace-settings-panel");
        setAccessibleText("Map settings panel");

        title.getStyleClass().add("workspace-panel-title");
        description.getStyleClass().add("workspace-panel-description");
        description.setWrapText(true);
        content.setFillWidth(true);

        categories.put("general", general());
        categories.put("rendering", rendering());
        categories.put("terrain", terrain());
        categories.put("objects", objects());
        categories.put("collision", diagnostics());
        categories.put("outliner", wrap(outliner));
        categories.put("inspector", wrap(inspector));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("workspace-settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(title, description, new Separator(), scroll);
        showCategory("general");
    }

    public void showCategory(String id) {
        Node selected = categories.getOrDefault(id, categories.get("general"));
        String label = labelFor(id);
        title.setText(label);
        description.setText(descriptionFor(id));
        content.getChildren().setAll(selected);
    }

    private VBox general() {
        return section(
                check("Show region boundaries", Options.showBorders),
                check("Show map-square names", Options.showMapFileNames),
                check("Show camera", Options.showCamera),
                check("Show tile information", Options.showTileInformation),
                check("Debug overlays", Options.showDebug));
    }

    private VBox rendering() {
        return section(
                check("HD textures", Options.hdTextures),
                check("HD map", Options.hdMap),
                check("Animations", Options.loadAnimations),
                check("Disable terrain blending", Options.disableBlending),
                check("Show objects", Options.showObjects));
    }

    private VBox terrain() {
        return section(
                check("Show overlays", Options.showOverlay),
                check("Show hidden tiles", Options.showHiddenTiles),
                check("Show all height levels", Options.allHeightsVisible),
                check("Simulate bridges", Options.simulateBridgesProperty),
                check("Display overlay IDs", Options.showOverlayNumbers),
                check("Display underlay IDs", Options.showUnderlayNumbers),
                check("Display tile heights", Options.showTileHeightNumbers));
    }

    private VBox objects() {
        return section(
                check("Show objects", Options.showObjects),
                check("Show map-icon objects", Options.showMinimapFunctionModels),
                check("Load animations", Options.loadAnimations));
    }

    private VBox diagnostics() {
        return section(
                check("Unwalkable flag", Options.showBlockedFlag),
                check("Bridge flag", Options.showBridgeFlag),
                check("Remove-roof flag", Options.showForceLowestPlaneFlag),
                check("Render on lower Z", Options.showLowerZFlag),
                check("Disable-render flag", Options.showDisableRenderFlag));
    }

    private static VBox wrap(Node node) {
        VBox box = new VBox(8, node);
        VBox.setVgrow(node, Priority.ALWAYS);
        return box;
    }

    private static VBox section(Node... nodes) {
        VBox box = new VBox(8, nodes);
        box.getStyleClass().add("workspace-settings-section");
        return box;
    }

    private static CheckBox check(String text, javafx.beans.property.BooleanProperty property) {
        CheckBox check = new CheckBox(text);
        check.selectedProperty().bindBidirectional(property);
        check.setAccessibleText(text);
        return check;
    }

    private static String labelFor(String id) {
        return switch (id) {
            case "general" -> "General map settings";
            case "rendering" -> "Rendering";
            case "terrain" -> "Terrain";
            case "objects" -> "Objects";
            case "collision" -> "Collision and diagnostics";
            case "outliner" -> "World outliner";
            case "inspector" -> "Inspector";
            default -> "Map settings";
        };
    }

    private static String descriptionFor(String id) {
        return switch (id) {
            case "general" -> "Control the map overlays and editor presentation without changing authored cache data.";
            case "rendering" -> "Presentation controls for the active scene. Brightness does not alter scene fingerprints.";
            case "terrain" -> "Choose which terrain layers and diagnostics are visible in the viewport.";
            case "objects" -> "Control object visibility and preview behavior while editing locations.";
            case "collision" -> "Inspect movement, projectile, route, bridge, and occluder diagnostics.";
            case "outliner" -> "Navigate loaded regions, planes, terrain layers, and object categories.";
            case "inspector" -> "Inspect the currently selected tile, object, region, or diagnostic projection.";
            default -> "Map editor settings.";
        };
    }
}
