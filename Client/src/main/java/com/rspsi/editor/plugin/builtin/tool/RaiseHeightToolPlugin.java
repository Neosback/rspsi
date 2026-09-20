package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.ChangeHeightTool;

public final class RaiseHeightToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.height.raise";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 13; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.raise", "Raise height", "Height", "Height",
                "UP", "3", 10,
                () -> {
                    var tool = new ChangeHeightTool(8);
                    settings.configure("terrain.raise", tool);
                    return tool;
                }
        );
    }
}
