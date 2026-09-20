package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.RotateObjectTool;

public final class RotateObjectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.object.rotate";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 22; }

    @Override
    public void initialize(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());
        context.registry().registerTool(
                "object.rotate", "Rotate object", "Objects", "Objects",
                "ROTATE", null, 30,
                () -> {
                    var tool = new RotateObjectTool();
                    settings.configure("object.rotate", tool);
                    return tool;
                }
        );
    }
}
