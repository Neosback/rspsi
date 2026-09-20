package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.DuplicateSelectionTool;

public final class DuplicateSelectionToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.duplicate";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 35; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.duplicate", "Duplicate selection", "Selection", "Selector",
                "DUPLICATE", "Shift+D", 60,
                () -> {
                    var tool = new DuplicateSelectionTool();
                    settings.configure("selection.duplicate", tool);
                    return tool;
                }
        );
    }
}
