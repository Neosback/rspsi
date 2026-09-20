package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.SmoothTerrainTool;

public final class SmoothTerrainToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.height.smooth";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 16; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.smooth", "Smooth terrain", "Height", "Height",
                "SMOOTH", null, 40,
                () -> {
                    var tool = new SmoothTerrainTool(50);
                    settings.configure("terrain.smooth", tool);
                    return tool;
                }
        );
    }
}
