package com.rspsi.ui.workspace;

import com.rspsi.editor.plugin.EditorMenuRegistration;
import com.rspsi.editor.plugin.EditorPluginHost;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** JavaFX command-palette projection over shell-owned plugin commands. */
public final class PluginCommandPalettePanel extends VBox {
    private final TextField search = new TextField();
    private final ListView<EditorMenuRegistration> results = new ListView<>();
    private final Button run = new Button("Run");
    private final Label status = new Label("No plugin commands registered.");
    private EditorPluginHost pluginHost;

    public PluginCommandPalettePanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("workspace-plugin-command-palette");
        setAccessibleText("Plugin command palette");

        Label title = new Label("Plugin commands");
        title.getStyleClass().add("workspace-panel-title");
        search.setPromptText("Search commands");
        search.setAccessibleText("Search plugin commands");
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());

        results.setAccessibleText("Plugin command results");
        results.setCellFactory(view -> new CommandCell());
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                runSelected();
                event.consume();
            }
        });
        VBox.setVgrow(results, Priority.ALWAYS);

        run.setAccessibleText("Run selected plugin command");
        run.setOnAction(event -> runSelected());
        status.getStyleClass().add("workspace-panel-status");
        getChildren().addAll(title, search, results, run, status);
    }

    public void bindPluginHost(EditorPluginHost host) {
        pluginHost = Objects.requireNonNull(host, "host");
        refresh();
    }

    private void refresh() {
        List<EditorMenuRegistration> menus = pluginHost == null
                ? List.of()
                : pluginHost.registry().menuRegistrations().stream()
                .filter(menu -> matches(menu, search.getText()))
                .toList();
        EditorMenuRegistration selected = results.getSelectionModel().getSelectedItem();
        results.setItems(FXCollections.observableArrayList(menus));
        if (selected != null) results.getSelectionModel().select(selected);
        boolean canRun = pluginHost != null && pluginHost.context().session().canEdit();
        run.setDisable(!canRun || results.getItems().isEmpty());
        status.setText(pluginHost == null
                ? "No plugin host mounted."
                : menus.size() + (menus.size() == 1 ? " command" : " commands")
                        + (canRun ? "" : " · read-only"));
    }

    private void runSelected() {
        if (pluginHost == null || !pluginHost.context().session().canEdit()) return;
        EditorMenuRegistration selected = results.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        pluginHost.context().session().execute(
                pluginHost.registry().createCommand(selected.commandId()));
        status.setText("Ran " + selected.label());
    }

    private static boolean matches(EditorMenuRegistration menu, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        String haystack = (String.join(" ", menu.path()) + " " + menu.label())
                .toLowerCase(Locale.ROOT);
        return haystack.contains(normalized);
    }

    private static final class CommandCell extends ListCell<EditorMenuRegistration> {
        @Override
        protected void updateItem(EditorMenuRegistration item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null
                    : String.join(" / ", item.path()) + " / " + item.label());
        }
    }
}
