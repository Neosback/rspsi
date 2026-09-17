package com.rspsi.editor.plugin;

import com.rspsi.editor.input.EditorKeyEvent;

/** Neutral shortcut behavior; the frontend owns only event translation and focus. */
@FunctionalInterface
public interface EditorShortcut {
    boolean handle(EditorPluginContext context, EditorKeyEvent event);
}
