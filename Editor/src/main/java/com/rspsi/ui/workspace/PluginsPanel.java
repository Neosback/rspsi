package com.rspsi.ui.workspace;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager.PluginStatus;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Frontend projection of {@link EditorPluginLifecycleManager}: one row per
 * candidate plugin with an enable checkbox, a status line, and an
 * enable-all button.
 *
 * <p>The panel owns no lifecycle decisions; it only renders the manager's
 * state and forwards user intent. Toggle actions run on the JavaFX thread
 * and the manager rebuilds the host synchronously.</p>
 */
public final class PluginsPanel extends VBox implements AutoCloseable {
    private final ObjectProperty<EditorPluginLifecycleManager> manager =
            new SimpleObjectProperty<>(this, "manager");
    private final Map<String, PluginRow> rows = new LinkedHashMap<>();
    private final Button enableAll = new Button("Enable all plugins");
    private java.util.function.Consumer<com.rspsi.editor.plugin.EditorPluginHost> hostListener;

    public PluginsPanel() {
        getStyleClass().add("plugins-panel");
        setSpacing(6);
        setPadding(new Insets(8));
        enableAll.getStyleClass().add("plugins-enable-all");
        enableAll.setAccessibleText("Enable all plugins");
        enableAll.setOnAction(event -> {
            EditorPluginLifecycleManager current = manager.get();
            if (current != null) {
                current.enableAll();
                refresh();
                notifyHostRebuilt();
            }
        });
        getChildren().add(enableAll);
        manager.addListener((observable, oldValue, newValue) -> rebuild());
        refresh();
    }

    /** Mounts the lifecycle manager whose candidates this panel renders. */
    public void bindPluginHost(EditorPluginLifecycleManager value) {
        manager.set(value);
    }

    /** Registers a callback that receives the rebuilt host after every toggle. */
    public void onHostRebuilt(
            java.util.function.Consumer<com.rspsi.editor.plugin.EditorPluginHost> listener) {
        this.hostListener = listener;
    }

    private void notifyHostRebuilt() {
        EditorPluginLifecycleManager current = manager.get();
        if (hostListener != null && current != null && current.host() != null) {
            hostListener.accept(current.host());
        }
    }

    public EditorPluginLifecycleManager manager() {
        return manager.get();
    }

    private void rebuild() {
        rows.clear();
        getChildren().removeIf(node -> node != enableAll);
        refresh();
    }

    /** Re-reads statuses from the mounted manager. */
    public void refresh() {
        EditorPluginLifecycleManager current = manager.get();
        enableAll.setVisible(current != null && !current.candidates().isEmpty());
        enableAll.setManaged(current != null && !current.candidates().isEmpty());
        if (current == null) {
            rows.forEach((id, row) -> row.update(PluginStatus.ENABLED, null));
            return;
        }
        for (EditorPlugin candidate : current.candidates()) {
            PluginRow row = rows.get(candidate.id());
            if (row == null) {
                row = new PluginRow(candidate);
                rows.put(candidate.id(), row);
                getChildren().add(row.box());
            }
            row.update(current.status(candidate.id()), current);
        }
    }

    private final class PluginRow {
        private final EditorPlugin candidate;
        private final CheckBox toggle = new CheckBox();
        private final Label status = new Label();
        private final VBox box;

        PluginRow(EditorPlugin candidate) {
            this.candidate = Objects.requireNonNull(candidate, "candidate");
            String name = descriptorName(candidate);
            toggle.setText(name);
            toggle.getStyleClass().add("plugin-toggle");
            toggle.setAccessibleText("Enable plugin " + name);
            toggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
                EditorPluginLifecycleManager current = manager.get();
                if (current == null || !current.candidates().stream()
                        .anyMatch(plugin -> plugin.id().equals(candidate.id()))) {
                    return;
                }
                current.setEnabled(candidate.id(), Boolean.TRUE.equals(newValue));
                refresh();
                notifyHostRebuilt();
            });
            status.getStyleClass().add("plugin-status");
            status.setWrapText(true);
            box = new VBox(2, toggle, status);
            box.getStyleClass().add("plugin-row");
        }

        VBox box() {
            return box;
        }

        void update(PluginStatus pluginStatus, EditorPluginLifecycleManager source) {
            toggle.setSelected(pluginStatus == PluginStatus.ENABLED);
            boolean disabledByCascade = pluginStatus == PluginStatus.CASCADE_DISABLED;
            toggle.setDisable(source == null || disabledByCascade);
            String blockedBy = source == null ? null
                    : String.join(", ", source.dependentsOf(candidate.id()));
            status.setText(describe(pluginStatus, disabledByCascade));
            status.setTooltip(disabledByCascade && blockedBy != null && !blockedBy.isBlank()
                    ? new Tooltip("Required by: " + blockedBy)
                    : null);
        }

        private String describe(PluginStatus pluginStatus, boolean disabledByCascade) {
            return switch (pluginStatus) {
                case ENABLED -> "Enabled";
                case DISABLED -> "Disabled";
                case CASCADE_DISABLED -> "Off: depends on a disabled plugin";
            };
        }
    }

    private static String descriptorName(EditorPlugin candidate) {
        try {
            if (candidate.descriptor() != null && !candidate.descriptor().name().isBlank()) {
                return candidate.descriptor().name();
            }
        } catch (RuntimeException ignored) {
            // Fall back to the raw id below.
        }
        return candidate.id();
    }

    @Override
    public void close() {
        manager.set(null);
        refresh();
    }
}
