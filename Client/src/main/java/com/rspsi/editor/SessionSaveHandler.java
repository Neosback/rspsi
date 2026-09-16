package com.rspsi.editor;

/**
 * UI- and cache-neutral persistence callback owned by an editor session.
 * Implementations perform the format-specific save and call
 * {@link EditorSession#markSaved()} only after the output is durable.
 */
@FunctionalInterface
public interface SessionSaveHandler {
    void save(EditorSession session);
}
