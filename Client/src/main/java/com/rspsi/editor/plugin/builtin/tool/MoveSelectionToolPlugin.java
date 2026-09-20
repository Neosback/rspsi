package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.MoveSelectionTool;

public final class MoveSelectionToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.move";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 33; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.move", "Move selection", "Selection", "Selector",
                "MOVE", "W", 40,
                () -> {
                    var tool = new MoveSelectionTool();
                    settings.configure("selection.move", tool);
                    return tool;
                }
        );
    }
}
