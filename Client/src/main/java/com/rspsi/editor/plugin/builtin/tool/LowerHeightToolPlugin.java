package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.ChangeHeightTool;

public final class LowerHeightToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.height.lower";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 14; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.lower", "Lower height", "Height", "Height",
                "DOWN", "Shift+3", 20,
                () -> {
                    var tool = new ChangeHeightTool(-8);
                    settings.configure("terrain.lower", tool);
                    return tool;
                }
        );
    }
}
