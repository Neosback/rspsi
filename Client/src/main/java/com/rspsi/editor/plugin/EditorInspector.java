package com.rspsi.editor.plugin;

import java.util.List;

/** Neutral inspector provider; frontends decide layout and editing affordances. */
@FunctionalInterface
public interface EditorInspector {
    List<EditorInspectorField> inspect(EditorPluginContext context);
}
