package com.rspsi.editor.plugin;

import java.util.List;

/** Provides dynamic, shell-rendered status values without owning the status bar. */
@FunctionalInterface
public interface EditorStatusContribution {
    List<EditorStatusItem> items(EditorPluginContext context);
}
