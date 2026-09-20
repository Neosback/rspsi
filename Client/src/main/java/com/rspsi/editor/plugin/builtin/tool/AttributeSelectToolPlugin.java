package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.SelectionToolSettings;
import com.rspsi.editor.tool.AttributeSelectionTool;

public final class AttributeSelectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection.attribute";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 32; }

    @Override
    public void initialize(EditorPluginContext context) {
        SelectionToolSettings settings = new SelectionToolSettings(context.settings());
        context.registry().registerTool(
                "selection.attribute", "Select by attribute", "Selection", "Selector",
                "SELECT_ATTR", null, 30,
                () -> {
                    var tool = new AttributeSelectionTool();
                    settings.configure("selection.attribute", tool);
                    return tool;
                }
        );
    }
}
