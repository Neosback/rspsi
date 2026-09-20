package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.PlaceObjectTool;

public final class PlaceObjectToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.object.place";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 20; }

    @Override
    public void initialize(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());
        context.registry().registerTool(
                "object.place", "Place object", "Objects", "Objects",
                "ADD", "4", 10,
                () -> {
                    var tool = new PlaceObjectTool(0, 10, 0);
                    settings.configure("object.place", tool);
                    return tool;
                }
        );
    }
}
