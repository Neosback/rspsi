package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.PaintFlagsTool;

public final class PaintFlagsToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.terrain.flags";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 12; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.flags", "Paint flags", "Terrain", "Paint",
                "FLAGS", null, 30,
                () -> {
                    var tool = new PaintFlagsTool(0);
                    settings.configure("terrain.flags", tool);
                    return tool;
                }
        );
    }
}
