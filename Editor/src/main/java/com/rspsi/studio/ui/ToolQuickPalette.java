package com.rspsi.studio.ui;

import com.rspsi.studio.theme.StudioPalette;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

/**
 * Host-owned viewport Quick Palette for the active map tool.
 *
 * <p>Extensions provide frontend-neutral {@code EditorUiNode} content through
 * their tool descriptor. Studio owns placement, chrome and native rendering.</p>
 */
public final class ToolQuickPalette {
    private static final float OFFSET_X = 74.0f;
    private static final float OFFSET_Y = 20.0f;
    private static final float MAX_WIDTH = 320.0f;

    private static final int FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.AlwaysAutoResize;

    private final DeclarativeToolUiRenderer renderer = new DeclarativeToolUiRenderer();

    public void render(
            StudioPanelContext context,
            float viewportX,
            float viewportY,
            float viewportWidth,
            float viewportHeight) {
        if (context == null || context.studioPlugins() == null || context.activeToolId() == null) {
            return;
        }

        var tool = context.studioPlugins().toolView(context.activeToolId()).orElse(null);
        if (tool == null) return;
        var node = tool.quickPaletteNode().orElse(null);
        if (node == null) return;

        float availableWidth = Math.max(160.0f, viewportWidth - OFFSET_X - 20.0f);
        float width = Math.min(MAX_WIDTH, availableWidth);
        float x = viewportX + OFFSET_X;
        float y = viewportY + OFFSET_Y;

        // Clamp the host-owned palette to the current viewport on every frame.
        float maxX = viewportX + Math.max(0.0f, viewportWidth - width - 8.0f);
        float maxY = viewportY + Math.max(0.0f, viewportHeight - 80.0f);
        x = Math.min(x, maxX);
        y = Math.min(y, maxY);

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, 0.0f, ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, StudioPalette.BORDER_STRONG);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 8.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 10.0f, 8.0f);

        if (ImGui.begin("##ActiveToolQuickPalette", FLAGS)) {
            ImGui.textColored(StudioPalette.ACCENT, tool.name());
            ImGui.separator();
            renderer.render(node);
        }
        ImGui.end();

        ImGui.popStyleVar(2);
        ImGui.popStyleColor();
    }
}
