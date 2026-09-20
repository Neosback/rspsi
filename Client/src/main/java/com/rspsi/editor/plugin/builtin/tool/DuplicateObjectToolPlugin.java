package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.DuplicateObjectTool;

public final class DuplicateObjectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.object.duplicate";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 23; }

    @Override
    public void initialize(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());
        context.registry().registerTool(
                "object.duplicate", "Duplicate object", "Objects", "Objects",
                "DUPLICATE", null, 40,
                () -> {
                    var tool = new DuplicateObjectTool();
                    settings.configure("object.duplicate", tool);
                    return tool;
                }
        );
    }
}
