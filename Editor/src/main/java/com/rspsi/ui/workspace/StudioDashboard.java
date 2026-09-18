package com.rspsi.ui.workspace;

import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Reusable startup surface for workspace and Studio-settings navigation.
 * It deliberately owns no cache, renderer, project, or session state.
 */
public final class StudioDashboard extends BorderPane {
    private final BiConsumer<String, String> openMapEditor;
    private final Runnable openContentSettings;
    private final Runnable openStudioSettings;
    private final Runnable chooseCache;
    private final TextField cacheField = new TextField();
    private final TextField regionField = new TextField();

    public StudioDashboard(Runnable openMapEditor,
                           Runnable openContentSettings,
                           Runnable openStudioSettings,
                           Runnable chooseCache) {
        this((cache, region) -> openMapEditor.run(), openContentSettings, openStudioSettings,
                chooseCache);
    }

    /** Startup dashboard constructor with explicit cache and optional debug region. */
    public StudioDashboard(BiConsumer<String, String> openMapEditor,
                           Runnable openContentSettings,
                           Runnable openStudioSettings,
                           Runnable chooseCache) {
        this.openMapEditor = Objects.requireNonNull(openMapEditor, "openMapEditor");
        this.openContentSettings = Objects.requireNonNull(openContentSettings, "openContentSettings");
        this.openStudioSettings = Objects.requireNonNull(openStudioSettings, "openStudioSettings");
        this.chooseCache = Objects.requireNonNull(chooseCache, "chooseCache");
        getStyleClass().add("studio-dashboard");
        build();
    }

    private void build() {
        Label eyebrow = new Label("OPENRUNE CONTENT STUDIO");
        eyebrow.getStyleClass().add("studio-dashboard-eyebrow");
        Label title = new Label("What do you want to build?");
        title.getStyleClass().add("studio-dashboard-title");
        Label subtitle = new Label("Choose a workspace or configure the project before opening an editor.");
        subtitle.getStyleClass().add("studio-dashboard-subtitle");

        Label cacheTitle = new Label("Session source");
        cacheTitle.getStyleClass().add("studio-dashboard-section-heading");
        cacheField.setPromptText("Select an OSRS cache directory");
        cacheField.setEditable(false);
        cacheField.setAccessibleText("Selected OSRS cache directory");
        Button browse = secondaryButton("Choose cache", StudioIcon.ASSETS, chooseCache);
        Label cacheStatus = new Label();
        cacheStatus.getStyleClass().add("studio-dashboard-cache-status");
        Runnable refreshCacheStatus = () -> cacheStatus.setText(cacheField.getText().isBlank()
                ? "No cache selected — Map Editor will open in its empty state."
                : "Cache selected. Map Editor will reuse this source without asking again.");
        cacheField.textProperty().addListener((observable, oldValue, newValue) -> refreshCacheStatus.run());
        refreshCacheStatus.run();
        HBox cacheRow = new HBox(8, cacheField, browse);
        cacheRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cacheField, Priority.ALWAYS);
        regionField.setPromptText("Optional: regionX,regionY or region ID (debug launch)");
        regionField.setAccessibleText("Optional direct region for map editor debug launch");
        HBox startup = new HBox(8, cacheRow, regionField);
        startup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cacheRow, Priority.ALWAYS);
        HBox.setHgrow(regionField, Priority.ALWAYS);
        VBox source = new VBox(6, cacheTitle, startup, cacheStatus);
        source.getStyleClass().add("studio-dashboard-source");

        // Use the same cache and optional debug region as the workspace card.
        // A separate no-argument launch callback could discard that selection
        // and send the user through another setup surface.
        Button open = primaryButton("Open Map Editor", StudioIcon.TERRAIN,
                () -> openMapEditor.accept(cacheField.getText(), regionField.getText()));
        Button settings = secondaryButton("Studio settings", StudioIcon.SETTINGS, openStudioSettings);
        HBox actions = new HBox(8, open, settings);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(5, eyebrow, title, subtitle, actions, source);
        header.setMaxWidth(920);

        Label workspaceHeading = sectionHeading("Workspaces");
        GridPane workspaces = new GridPane();
        workspaces.setHgap(12);
        workspaces.setVgap(12);
        workspaces.getColumnConstraints().addAll(column(), column(), column());
        workspaces.add(workspaceCard("Map Editor", "Edit terrain, objects, heights, collision, and regions.",
                StudioIcon.TERRAIN, "Open Map Editor", () -> openMapEditor.accept(cacheField.getText(), regionField.getText()), false), 0, 0);
        workspaces.add(workspaceCard("Asset Studio", "Browse and inspect models, sprites, textures, and definitions.",
                StudioIcon.ASSETS, "Coming later", null, true), 1, 0);
        workspaces.add(workspaceCard("Interface Studio", "Build interfaces and widgets against the shared project session.",
                StudioIcon.INSPECTOR, "Coming later", null, true), 2, 0);
        workspaces.add(workspaceCard("Model Studio", "Inspect, transform, and prepare model assets.",
                StudioIcon.OBJECT, "Coming later", null, true), 0, 1);
        workspaces.add(workspaceCard("Cutscene Studio", "Author camera, scene, and event timelines.",
                StudioIcon.VALIDATION, "Coming later", null, true), 1, 1);
        workspaces.add(workspaceCard("Build & Validation", "Inspect compatibility, staged output, and build diagnostics.",
                StudioIcon.BUILD, "Coming later", null, true), 2, 1);

        Label settingsHeading = sectionHeading("Studio configuration");
        HBox settingsCards = new HBox(12,
                settingsCard("OpenRune Content Studio", "Server adapter, cache source/output, GameVals, packs, and build integration.",
                        StudioIcon.BUILD, openContentSettings),
                settingsCard("RSPSi Studio", "Theme, layout, keybindings, renderer presentation, autosave, and diagnostics.",
                        StudioIcon.SETTINGS, openStudioSettings));
        HBox.setHgrow(settingsCards.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(settingsCards.getChildren().get(1), Priority.ALWAYS);

        VBox content = new VBox(22, header, new Separator(), workspaceHeading, workspaces,
                settingsHeading, settingsCards);
        content.setMaxWidth(1120);
        content.setPadding(new Insets(42, 48, 48, 48));
        content.getStyleClass().add("studio-dashboard-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("studio-dashboard-scroll");
        setCenter(scroll);
    }

    public String cachePath() {
        return cacheField.getText();
    }

    public String startupRegion() {
        return regionField.getText();
    }

    public void setCachePath(String value) {
        cacheField.setText(value == null ? "" : value);
    }

    private VBox workspaceCard(String title, String description, StudioIcon icon,
                               String actionLabel, Runnable action, boolean disabled) {
        Label label = new Label(title);
        label.getStyleClass().add("studio-dashboard-card-title");
        Label detail = new Label(description);
        detail.setWrapText(true);
        detail.getStyleClass().add("studio-dashboard-card-detail");
        Button actionButton = disabled
                ? secondaryButton(actionLabel, icon, () -> { })
                : primaryButton(actionLabel, icon, action);
        actionButton.setDisable(disabled);
        VBox card = new VBox(10, StudioIconFactory.icon(icon), label, detail, actionButton);
        card.setPrefHeight(172);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("studio-dashboard-card", disabled
                ? "studio-dashboard-card-disabled" : "studio-dashboard-card-enabled");
        return card;
    }

    private VBox settingsCard(String title, String description, StudioIcon icon, Runnable action) {
        Label label = new Label(title);
        label.getStyleClass().add("studio-dashboard-card-title");
        Label detail = new Label(description);
        detail.setWrapText(true);
        detail.getStyleClass().add("studio-dashboard-card-detail");
        Button button = secondaryButton("Open settings", icon, action);
        VBox card = new VBox(8, label, detail, button);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("studio-dashboard-settings-card");
        return card;
    }

    private static ColumnConstraints column() {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(33.3333);
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    private static Label sectionHeading(String value) {
        Label heading = new Label(value);
        heading.getStyleClass().add("studio-dashboard-section-heading");
        return heading;
    }

    private static Button primaryButton(String label, StudioIcon icon, Runnable action) {
        Button button = button(label, icon, action);
        button.getStyleClass().add("studio-dashboard-primary");
        return button;
    }

    private static Button secondaryButton(String label, StudioIcon icon, Runnable action) {
        Button button = button(label, icon, action);
        button.getStyleClass().add("studio-dashboard-secondary");
        return button;
    }

    private static Button button(String label, StudioIcon icon, Runnable action) {
        Button button = new Button(label, StudioIconFactory.icon(icon));
        button.setOnAction(event -> action.run());
        button.setAccessibleText(label);
        button.setFocusTraversable(true);
        return button;
    }
}
