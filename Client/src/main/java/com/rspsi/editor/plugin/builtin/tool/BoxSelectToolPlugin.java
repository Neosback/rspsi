package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.BoxSelectTool;

public final class BoxSelectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.box";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 30; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.box", "Box select", "Selection", "Selector",
                "SELECT_BOX", "1", 10,
                () -> {
                    var tool = new BoxSelectTool();
                    settings.configure("selection.box", tool);
                    return tool;
                }
        );
    }
}
