package com.rspsi.ui.workspace;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionStateListener;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.Objects;

/**
 * Compact, persistent workspace state for the controlled JavaFX shell.
 *
 * <p>The bar deliberately reports facts supplied by the neutral session
 * binding rather than reading cache or renderer state. It keeps the editor's
 * most important safety signals visible: OSRS context, editability, cache
 * revision, and saved/dirty state.</p>
 */
public final class WorkspaceStatusBar extends HBox implements AutoCloseable {
    private final Label context = label("workspace-status-context");
    private final Label mode = label("workspace-status-mode");
    private final Label cache = label("workspace-status-cache");
    private final Label dirty = label("workspace-status-dirty");
    private final SessionStateListener stateListener = ignored -> refreshOnFxThread();
    private EditorSession session;
    private String contextText = "No project loaded";
    private String cacheText = "Cache: unavailable";
    private String compatibilityText = "";

    public WorkspaceStatusBar() {
        setSpacing(16);
        setPadding(new Insets(6, 10, 6, 10));
        getStyleClass().add("workspace-status-bar");
        setAccessibleText("Workspace status");
        getChildren().addAll(context, mode, cache, dirty);
        clear();
    }

    public void bind(EditorSession session, String contextText, String cacheText,
                     String compatibilityText) {
        if (this.session != null) {
            this.session.removeStateListener(stateListener);
        }
        this.session = Objects.requireNonNull(session, "session");
        this.contextText = nonBlank(contextText, "OSRS project");
        this.cacheText = nonBlank(cacheText, "Cache: unavailable");
        this.compatibilityText = compatibilityText == null ? "" : compatibilityText.trim();
        this.session.addStateListener(stateListener);
        refresh();
    }

    public void bind(EditorSession session, String contextText) {
        bind(session, contextText, "Cache: unavailable", "");
    }

    public void refresh() {
        if (session == null) {
            clear();
            return;
        }
        context.setText(contextText);
        mode.setText(session.canEdit() ? "Editable" : "Read-only");
        mode.getStyleClass().removeAll("workspace-status-editable", "workspace-status-readonly");
        mode.getStyleClass().add(session.canEdit()
                ? "workspace-status-editable" : "workspace-status-readonly");
        cache.setText(cacheText + (compatibilityText.isBlank() ? "" : " · " + compatibilityText));
        dirty.setText(session.isDirty() ? "Unsaved changes" : "Saved");
        dirty.getStyleClass().removeAll("workspace-status-dirty", "workspace-status-saved");
        dirty.getStyleClass().add(session.isDirty()
                ? "workspace-status-dirty" : "workspace-status-saved");
        setAccessibleText(contextText + ". " + mode.getText() + ". " + cache.getText()
                + ". " + dirty.getText());
    }

    private void clear() {
        context.setText(contextText);
        mode.setText("Waiting for session");
        cache.setText(cacheText);
        dirty.setText("");
        setAccessibleText("Workspace status. " + contextText + ". Waiting for session.");
    }

    private void refreshOnFxThread() {
        if (Platform.isFxApplicationThread()) refresh();
        else Platform.runLater(this::refresh);
    }

    private static Label label(String styleClass) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    @Override
    public void close() {
        if (session != null) {
            session.removeStateListener(stateListener);
            session = null;
        }
    }
}
