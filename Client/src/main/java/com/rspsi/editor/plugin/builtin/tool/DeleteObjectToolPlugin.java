package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.DeleteObjectTool;

public final class DeleteObjectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.object.delete";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 24; }

    @Override
    public void initialize(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());
        context.registry().registerTool(
                "object.delete", "Delete object", "Objects", "Objects",
                "DELETE", "Delete", 50,
                () -> {
                    var tool = new DeleteObjectTool();
                    settings.configure("object.delete", tool);
                    return tool;
                }
        );
    }
}
