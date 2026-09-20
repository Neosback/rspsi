package com.rspsi.studio.ui.hud;

import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;

/** Compact managed HUD summarizing the active Tile Painter configuration. */
public final class TilePainterHud implements StudioPlugin {
    public static final String ID = "studio.tile-painter-hud";

    @Override public String id() { return ID; }
    @Override public String name() { return "Tile Painter HUD"; }
    @Override public String description() { return "Compact live Tile Painter brush and material summary."; }
    @Override public String icon() { return StudioIcons.BRUSH; }
    @Override public boolean isConfigurable() { return false; }

    @Override
    public void renderHUD(StudioPanelContext context) {
        if (!"terrain.tile-painter".equals(context.activeToolId())) return;
        if (!(context.toolController().activeTool() instanceof CompositeTilePainterTool tool)) return;
        if (context.huds() == null) return;

        String text = "Brush " + tool.brush().name()
                + "  |  R " + tool.brushRadius()
                + "  |  U " + tool.underlayId()
                + "  |  O " + tool.overlayId()
                + "  |  S " + tool.shape()
                + "  |  Rot " + (tool.rotation() * 90) + "°";

        float padX = 10.0f;
        float padY = 4.0f;
        float width = ImGui.calcTextSize(text).x + padX * 2.0f;
        float height = 22.0f;
        var placement = context.huds().place(ViewportHudManager.Quadrant.BOTTOM_LEFT, width, height);

        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(placement.x(), placement.y(),
                placement.x() + width, placement.y() + height, 0xBF0F172A, 6.0f);
        draw.addRect(placement.x(), placement.y(),
                placement.x() + width, placement.y() + height, 0xBF334155, 6.0f, 0, 1.0f);
        draw.addText(placement.x() + padX, placement.y() + padY, 0xFFE2E8F0, text);
    }
}
