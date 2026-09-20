package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.PaintUnderlayTool;

public final class PaintUnderlayToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.terrain.underlay";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 11; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.paint-underlay", "Paint underlay", "Terrain", "Paint",
                "PENCIL", "Shift+2", 20,
                () -> {
                    var tool = new PaintUnderlayTool(1);
                    settings.configure("terrain.paint-underlay", tool);
                    return tool;
                }
        );
    }
}
