package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.MoveObjectTool;

public final class MoveObjectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.object.move";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 21; }

    @Override
    public void initialize(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());
        context.registry().registerTool(
                "object.move", "Move object", "Objects", "Objects",
                "MOVE", "Shift+4", 20,
                () -> {
                    var tool = new MoveObjectTool();
                    settings.configure("object.move", tool);
                    return tool;
                }
        );
    }
}
