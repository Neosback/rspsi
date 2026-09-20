package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.ReplaceSelectionTool;

public final class ReplaceSelectionToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.replace";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 36; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.replace", "Replace selection", "Selection", "Selector",
                "REPLACE", null, 70,
                () -> {
                    var tool = new ReplaceSelectionTool(0);
                    settings.configure("selection.replace", tool);
                    return tool;
                }
        );
    }
}
