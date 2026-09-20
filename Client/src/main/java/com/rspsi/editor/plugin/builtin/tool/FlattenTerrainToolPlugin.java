package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.FlattenTerrainTool;

public final class FlattenTerrainToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.height.flatten";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 15; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.flatten", "Flatten terrain", "Height", "Height",
                "FLATTEN", null, 30,
                () -> {
                    var tool = new FlattenTerrainTool(0);
                    settings.configure("terrain.flatten", tool);
                    return tool;
                }
        );
    }
}
