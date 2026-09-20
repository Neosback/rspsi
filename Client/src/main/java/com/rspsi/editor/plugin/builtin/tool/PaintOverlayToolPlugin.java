package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.builtin.TerrainToolSettings;
import com.rspsi.editor.tool.PaintOverlayTool;

public final class PaintOverlayToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.terrain.overlay";

    @Override public String id() { return ID; }
    @Override public int loadOrder() { return 10; }

    @Override
    public void initialize(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());
        context.registry().registerTool(
                "terrain.paint-overlay", "Paint overlay", "Terrain", "Paint",
                "BRUSH", "2", 10,
                () -> {
                    var tool = new PaintOverlayTool(1);
                    settings.configure("terrain.paint-overlay", tool);
                    return tool;
                }
        );
    }
}
