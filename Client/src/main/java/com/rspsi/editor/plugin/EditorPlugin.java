package com.rspsi.editor.plugin;

/** Neutral extension point for first-party editor workflows. */
public interface EditorPlugin {
    String id();

    /** Stable metadata and dependency declaration for lifecycle ordering. */
    default EditorPluginDescriptor descriptor() {
        return EditorPluginDescriptor.of(id());
    }

    /** Stable ordering hint for discovery; lower values initialize first. */
    default int loadOrder() {
        return 0;
    }

    default void initialize(EditorPluginContext context) {
    }

    /** Releases plugin-owned resources before its contributions are removed. */
    default void shutdown(EditorPluginContext context) {
    }
}
