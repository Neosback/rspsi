package com.rspsi.ui.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionChangeListener;
import com.rspsi.editor.SessionStateListener;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.validation.ValidationIssue;
import com.rspsi.editor.validation.WorldValidator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/** JavaFX diagnostic view over the neutral world validator. */
public final class ValidationPanel extends VBox implements AutoCloseable {
    private final Label status = new Label();
    private final ListView<String> issues = new ListView<>();
    private final SessionChangeListener changeListener = ignored -> refreshOnFxThread();
    private final SessionStateListener stateListener = ignored -> refreshOnFxThread();
    private EditorSession session;
    private DefinitionProvider definitions;

    public ValidationPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("workspace-session-panel");
        setAccessibleText("Validation diagnostics panel");

        Label title = new Label("Validation");
        title.getStyleClass().add("workspace-panel-title");
        status.getStyleClass().add("workspace-panel-status");
        issues.setPlaceholder(new Label("No validation issues found."));
        issues.setAccessibleText("World validation diagnostics");
        issues.setFocusTraversable(true);
        getChildren().addAll(title, status, issues);
        clear("Waiting for an editor session");
    }

    public void bind(EditorSession session) {
        bind(session, null);
    }

    /** Binds optional cache-backed definitions for object diagnostics. */
    public void bind(EditorSession session, DefinitionProvider definitions) {
        if (this.session != null) {
            this.session.removeChangeListener(changeListener);
            this.session.removeStateListener(stateListener);
        }
        this.session = Objects.requireNonNull(session, "session");
        this.definitions = definitions;
        this.session.addChangeListener(changeListener);
        this.session.addStateListener(stateListener);
        refresh();
    }

    public void refresh() {
        if (session == null) {
            clear("Waiting for an editor session");
            return;
        }
        List<ValidationIssue> diagnostics = WorldValidator.validate(session.world(), definitions);
        issues.getItems().setAll(diagnostics.stream().map(ValidationPanel::format).toList());
        long errors = diagnostics.stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
                .count();
        long warnings = diagnostics.size() - errors;
        status.setText(diagnostics.isEmpty()
                ? "World is valid"
                : String.format("%d error%s · %d warning%s",
                errors, errors == 1 ? "" : "s", warnings, warnings == 1 ? "" : "s"));
    }

    private static String format(ValidationIssue issue) {
        String location = issue.locationOptional()
                .map(ValidationPanel::formatLocation)
                .orElse("world");
        return String.format("[%s] %s · %s — %s",
                issue.severity(), issue.code(), location, issue.message());
    }

    private static String formatLocation(TileCoordinate coordinate) {
        return String.format("p%d/%d,%d", coordinate.plane(), coordinate.x(), coordinate.y());
    }

    private void clear(String message) {
        status.setText(message);
        issues.getItems().clear();
    }

    private void refreshOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            refresh();
        } else {
            Platform.runLater(this::refresh);
        }
    }

    @Override
    public void close() {
        if (session != null) {
            session.removeChangeListener(changeListener);
            session.removeStateListener(stateListener);
            session = null;
        }
        definitions = null;
    }
}
