package com.rspsi.editor.plugin;

import java.util.List;

/** Supplies the compact context-bar settings for an active tool. */
@FunctionalInterface
public interface EditorToolContextContribution {
    List<EditorSetting> settings(EditorPluginContext context);
}
