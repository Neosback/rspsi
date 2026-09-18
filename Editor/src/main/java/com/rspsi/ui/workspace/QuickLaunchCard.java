package com.rspsi.ui.workspace;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import com.rspsi.util.Settings;

/** Actionable empty state shown only before a map is loaded. */
public final class QuickLaunchCard extends VBox {
    public QuickLaunchCard(QuickLaunchHandler handler) {
        Objects.requireNonNull(handler, "handler");
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPadding(new Insets(22));
        getStyleClass().add("workspace-quick-launch-card");
        setMaxWidth(430);
        setMaxHeight(250);
        setAccessibleText("OpenRune Studio map quick launch");

        Label heading = new Label("Start a map editing session");
        heading.getStyleClass().add("workspace-quick-launch-title");
        Label description = new Label("Choose an existing map source or create a blank workspace.");
        description.getStyleClass().add("workspace-quick-launch-description");

        GridPane actions = new GridPane();
        actions.setHgap(8);
        actions.setVgap(8);
        Button cache = action("Open Local Cache", handler::openLocalCache);
        Button project = action("Open OSRS Project", handler::openProject);
        Button blank = action("New Blank Canvas", handler::createBlankCanvas);
        actions.add(cache, 0, 0);
        actions.add(project, 1, 0);
        actions.add(blank, 0, 1, 2, 1);

        TextField location = new TextField();
        location.setPromptText("x,y or region id");
        location.setAccessibleText("World coordinates or region ID");
        Button load = action("Load", () -> {
            String value = location.getText() == null ? "" : location.getText().trim();
            if (value.isBlank()) return;
            if (value.contains(",")) handler.openCoordinates(value);
            else handler.openRegionId(value);
        });
        load.setDefaultButton(true);
        VBox direct = new VBox(4, new Label("Direct coordinate / region jump"), new javafx.scene.layout.HBox(6, location, load));
        direct.getStyleClass().add("workspace-quick-launch-direct");

        ComboBox<String> recent = new ComboBox<>();
        recent.setPromptText("Recent regions");
        recent.setMaxWidth(Double.MAX_VALUE);
        recent.getItems().addAll(loadRecent());
        recent.setOnAction(event -> {
            String value = recent.getValue();
            if (value == null || value.isBlank()) return;
            if (value.contains(",")) handler.openCoordinates(value);
            else handler.openRegionId(value);
        });
        recent.setAccessibleText("Recently opened regions");
        getChildren().addAll(heading, description, actions, direct, recent);
    }

    public void recordRecent(String value) {
        if (value == null || value.isBlank()) return;
        List<String> values = new ArrayList<>(loadRecent());
        values.remove(value);
        values.add(0, value);
        if (values.size() > 5) values = new ArrayList<>(values.subList(0, 5));
        Settings.properties.put("recent_regions", values);
        Settings.saveSettings();
    }

    private static List<String> loadRecent() {
        Object value = Settings.properties.get("recent_regions");
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static Button action(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        button.setAccessibleText(text);
        return button;
    }
}
