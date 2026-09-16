package com.rspsi.ui.workspace;

import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionChangeListener;
import com.rspsi.editor.SessionStateListener;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** JavaFX view of the canonical command history; it owns no edit state. */
public final class SessionHistoryPanel extends VBox implements AutoCloseable {
    private final Label status = new Label();
    private final ListView<String> entries = new ListView<>();
    private final SessionChangeListener changeListener = ignored -> refreshOnFxThread();
    private final SessionStateListener stateListener = ignored -> refreshOnFxThread();
    private EditorSession session;

    public SessionHistoryPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("workspace-session-panel");
        setAccessibleText("History panel");
        Label title = new Label("History");
        title.getStyleClass().add("workspace-panel-title");
        status.getStyleClass().add("workspace-panel-status");
        entries.setPlaceholder(new Label("No edits yet."));
        entries.setAccessibleText("Editor command history");
        entries.setFocusTraversable(true);
        getChildren().addAll(title, status, entries);
        refresh();
    }

    public void bind(EditorSession session) {
        if (this.session != null) {
            this.session.removeChangeListener(changeListener);
            this.session.removeStateListener(stateListener);
        }
        this.session = Objects.requireNonNull(session, "session");
        this.session.addChangeListener(changeListener);
        this.session.addStateListener(stateListener);
        refresh();
    }

    public void refresh() {
        entries.getItems().clear();
        if (session == null) {
            status.setText("Waiting for an editor session");
            return;
        }
        int cursor = session.history().cursor();
        var commands = session.history().commands();
        for (int index = 0; index < commands.size(); index++) {
            EditorCommand command = commands.get(index);
            String state = index < cursor ? "Applied" : "Redo available";
            entries.getItems().add(String.format("%02d  %s  %s", index + 1, state, command.description()));
        }
        status.setText(String.format("%d applied · %s", cursor, session.isDirty() ? "Unsaved changes" : "Saved"));
        if (!entries.getItems().isEmpty()) {
            entries.getSelectionModel().select(Math.max(0, cursor - 1));
        }
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
    }
}
