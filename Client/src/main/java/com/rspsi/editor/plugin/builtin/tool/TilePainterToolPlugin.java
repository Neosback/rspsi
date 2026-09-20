package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.tool.CompositeTilePainterTool;

public final class TilePainterToolPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.terrain.painter";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int loadOrder() {
        return 10;
    }

    @Override
    public void initialize(EditorPluginContext context) {
        context.registry().registerTool(
                "terrain.tile-painter", "Tile painter", "Terrain", "Paint",
                "BRUSH", "2", 10,
                CompositeTilePainterTool::new
        );
    }
}
