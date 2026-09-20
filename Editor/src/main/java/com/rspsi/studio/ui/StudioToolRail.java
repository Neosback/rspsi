package com.rspsi.studio.ui;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dedicated vertical Left Tool Rail (TOOL_RAIL slot) docked to the left edge of the viewport.
 *
 * Provides exclusive modal tool selection (Tool Mutex) for Selection, Tile Painter,
 * Height Sculptor, Path Builder, and Object Spawner tools.
 */
public final class StudioToolRail {

    public static final float RAIL_WIDTH = 46.0f;

    private static final int RAIL_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings;

    public void render(StudioPanelContext context,
                       float x, float y, float height,
                       Consumer<String> activateTool,
                       String activeToolId) {

        ImGui.setNextWindowPos(x, y, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowSize(RAIL_WIDTH, height, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF141A24); // Dark slate matching right rail
        ImGui.pushStyleColor(ImGuiCol.Border, 0xFF232D3F);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4.0f, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);

        ImGui.begin("StudioLeftToolRail", RAIL_FLAGS);

        if (context.studioPlugins() != null) {
            List<StudioToolPlugin> toolPlugins = context.studioPlugins().toolPlugins();

            for (StudioToolPlugin tool : toolPlugins) {
                boolean isSel = tool.toolId().equals(activeToolId) || tool.id().equals(activeToolId);

                if (isSel) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFF2563EB); // Vibrant active blue
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF3B82F6);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFF1E293B);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF334155);
                }

                ImGui.pushFont(StudioFonts.icon(), 0.0f);
                if (ImGui.button(tool.icon() + "##tool-rail-" + tool.id(), 38.0f, 38.0f)) {
                    activateTool.accept(tool.toolId());
                }
                ImGui.popFont();
                ImGui.popStyleColor(2);

                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.textColored(0xFF38BDF8, tool.name() + " (" + tool.shortcut() + ")");
                    ImGui.separator();
                    ImGui.textDisabled(tool.description());
                    ImGui.endTooltip();
                }
            }
        }

        ImGui.end();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }
}
