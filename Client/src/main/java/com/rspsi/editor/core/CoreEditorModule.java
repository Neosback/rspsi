package com.rspsi.editor.core;

import com.rspsi.editor.plugin.EditorPluginContext;

/**
 * Compile-time OpenRune Studio feature module.
 *
 * <p>Core modules are part of the application, are always installed, and are
 * not managed as external plugins. They register into the same neutral runtime
 * registry and services used by extensions, so there is one implementation
 * path for tools and editor behavior.</p>
 */
public interface CoreEditorModule {
    String id();

    /** Compatibility/version identity exposed to external extension dependency checks. */
    default String version() {
        return "0.1.0";
    }

    /** Deterministic installation order. Lower values install first. */
    default int order() {
        return 0;
    }

    /** Registers this module into the shared editor runtime. */
    void install(EditorPluginContext context);
}
