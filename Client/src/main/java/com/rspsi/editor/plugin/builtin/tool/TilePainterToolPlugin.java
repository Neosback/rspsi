package com.rspsi.editor.plugin.builtin.tool;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;

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
        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.tile-palette", "Tile Painter", "palette",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM, EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true, true, "terrain.tile-painter", 5));
        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.tile-painter-hud", "Tile Painter HUD", "brush",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY, EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false, true, "terrain.tile-painter", 30));
    }
}
