package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.RampTerrainTool;

public final class RampTerrainToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.height.ramp";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 17; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.ramp", "Ramp terrain", "Height", "Height",
                "RAMP", null, 50,
                () -> {
                    var tool = new RampTerrainTool(0, 64);
                    settings.configure("terrain.ramp", tool);
                    return tool;
                }
        );
    }
}
