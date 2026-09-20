package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.LassoSelectTool;

public final class LassoSelectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.lasso";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 31; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.lasso", "Lasso select", "Selection", "Selector",
                "SELECT_LASSO", "Shift+1", 20,
                () -> {
                    var tool = new LassoSelectTool();
                    settings.configure("selection.lasso", tool);
                    return tool;
                }
        );
    }
}
