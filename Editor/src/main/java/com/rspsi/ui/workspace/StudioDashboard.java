package com.rspsi.ui.workspace;

import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
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
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.io.File;
import java.util.function.BiConsumer;

/**
 * Minimal startup surface for cache setup and workspace entry.
 * It deliberately owns no cache, renderer, project, or session state.
 */
public final class StudioDashboard extends BorderPane {
    private final BiConsumer<String, String> openMapEditor;
    private final Runnable chooseCache;
    private final TextField cacheField = new TextField();
    private final TextField regionField = new TextField();
    private Button cacheChooserButton;
    private Button mapEditorWorkspaceButton;
    private Label cacheStatus;
    private boolean cacheReady;

    /** Startup dashboard constructor with explicit cache and optional debug region. */
    public StudioDashboard(BiConsumer<String, String> openMapEditor,
                           Runnable chooseCache) {
        this.openMapEditor = Objects.requireNonNull(openMapEditor, "openMapEditor");
        this.chooseCache = Objects.requireNonNull(chooseCache, "chooseCache");
        getStyleClass().add("studio-dashboard");
        build();
    }

    private void build() {
        Label eyebrow = new Label("OPENRUNE STUDIO");
        eyebrow.getStyleClass().add("studio-dashboard-eyebrow");
        Label title = new Label("OpenRune Studio");
        title.getStyleClass().add("studio-dashboard-title");
        Label subtitle = new Label("Set up the cache once, then open Map Editor when it is ready.");
        subtitle.getStyleClass().add("studio-dashboard-subtitle");

        Label cacheTitle = new Label("Cache setup");
        cacheTitle.getStyleClass().add("studio-dashboard-section-heading");
        cacheField.setPromptText("Select an OSRS cache directory");
        cacheField.setEditable(false);
        cacheField.setAccessibleText("Selected OSRS cache directory");
        cacheChooserButton = primaryButton("Choose and load cache", StudioIcon.ASSETS, chooseCache);
        cacheStatus = new Label();
        cacheStatus.getStyleClass().add("studio-dashboard-cache-status");
        cacheField.textProperty().addListener((observable, oldValue, newValue) -> {
            cacheReady = false;
            refreshCacheControls();
        });
        HBox cacheRow = new HBox(8, cacheField, cacheChooserButton);
        cacheRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cacheField, Priority.ALWAYS);
        regionField.setText("50,50");
        regionField.setPromptText("Region X,Y, world X,Y, or region ID");
        regionField.setTooltip(new javafx.scene.control.Tooltip(
                "Starting region. Lumbridge is 50,50. You can also enter world coordinates or a region ID."));
        regionField.setAccessibleText("Starting region or world coordinates for Map Editor");
        regionField.setOnAction(event -> {
            if (cacheReady) openMapEditor.accept(cacheField.getText(), regionField.getText());
        });
        HBox startup = new HBox(8, cacheRow, regionField);
        startup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cacheRow, Priority.ALWAYS);
        HBox.setHgrow(regionField, Priority.ALWAYS);
        VBox source = new VBox(6, cacheTitle, startup, cacheStatus);
        source.getStyleClass().add("studio-dashboard-source");

        VBox header = new VBox(5, eyebrow, title, subtitle, source);
        header.setMaxWidth(920);

        Label workspaceHeading = sectionHeading("Workspaces");
        GridPane workspaces = new GridPane();
        workspaces.setHgap(12);
        workspaces.setVgap(12);
        workspaces.getColumnConstraints().add(column());
        workspaces.add(workspaceCard("Map Editor", "Edit terrain, objects, heights, collision, and regions.",
                StudioIcon.TERRAIN, "Open Map Editor", () -> openMapEditor.accept(cacheField.getText(), regionField.getText()), false), 0, 0);

        VBox content = new VBox(22, header, new Separator(), workspaceHeading, workspaces);
        content.setMaxWidth(1120);
        content.setPadding(new Insets(42, 48, 48, 48));
        content.getStyleClass().add("studio-dashboard-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("studio-dashboard-scroll");
        setCenter(scroll);
        setCacheStatus(new CacheSessionStatus(CacheSessionState.EMPTY, null, null,
                "Select a valid OSRS cache before opening Map Editor.", null));
    }

    public String cachePath() {
        return cacheField.getText();
    }

    public String startupRegion() {
        return regionField.getText();
    }

    public void setCachePath(String value) {
        cacheReady = false;
        cacheField.setText(value == null ? "" : value);
        refreshCacheControls();
    }

    public void setCacheStatus(CacheSessionStatus status) {
        Objects.requireNonNull(status, "status");
        cacheReady = status.state() == CacheSessionState.READY
                && status.currentSession().isPresent()
                && status.requestedPath() != null
                && new File(status.requestedPath().toString()).isDirectory();
        boolean showPath = status.state() != CacheSessionState.READY;
        cacheField.setVisible(showPath);
        cacheField.setManaged(showPath);
        if (cacheChooserButton != null) {
            cacheChooserButton.setText(cacheReady ? "Change cache" : "Choose and load cache");
        }
        String message = switch (status.state()) {
            case EMPTY -> "Select a valid OSRS cache before opening Map Editor.";
            case LOADING -> "Loading OpenRune cache… Map Editor will unlock when it is ready.";
            case READY -> status.currentSession().map(session ->
                    "Ready · " + session.path().getFileName() + " · " + session.backendName() + " · revision "
                            + session.identity().revision() + " · " + session.mapCount() + " map groups"
            ).orElse("OpenRune cache ready.");
            case FAILED -> status.message() + (status.failure() == null || status.failure().getMessage() == null
                    ? "" : " — " + status.failure().getMessage());
        };
        cacheStatus.setText(message);
        refreshCacheControls();
    }

    private void refreshCacheControls() {
        if (mapEditorWorkspaceButton != null) mapEditorWorkspaceButton.setDisable(!cacheReady);
        regionField.setDisable(!cacheReady);
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
        if ("Map Editor".equals(title)) mapEditorWorkspaceButton = actionButton;
        actionButton.setDisable(disabled || !cacheReady);
        VBox card = new VBox(10, StudioIconFactory.icon(icon), label, detail, actionButton);
        card.setPrefHeight(172);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("studio-dashboard-card", disabled
                ? "studio-dashboard-card-disabled" : "studio-dashboard-card-enabled");
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
