package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.RotateSelectionTool;

public final class RotateSelectionToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.rotate";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 34; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.rotate", "Rotate selection", "Selection", "Selector",
                "ROTATE", "R", 50,
                () -> {
                    var tool = new RotateSelectionTool();
                    settings.configure("selection.rotate", tool);
                    return tool;
                }
        );
    }
}
