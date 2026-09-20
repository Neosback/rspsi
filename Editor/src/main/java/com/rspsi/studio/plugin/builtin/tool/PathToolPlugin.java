package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Modal tool plugin for plotting paths, roads, and terrain incline ramps.
 */
public final class PathToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.path";
    public static final String ENGINE_TOOL_ID = "terrain.ramp";

    // Configurable settings
    private final ImInt defaultPathWidth = new ImInt(1);
    private final ImBoolean autoSmoothCorners = new ImBoolean(true);
    private final ImBoolean autoBlendEdges = new ImBoolean(true);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Path Builder";
    }

    @Override
    public String description() {
        return "Plot sequential points to generate smooth cobblestone paths, dirt trails, and uniform incline ramps.";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String author() {
        return "OpenRune Team";
    }

    @Override
    public String icon() {
        return StudioIcons.PATH;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "B";
    }

    @Override
    public int railPriority() {
        return 40;
    }

    @Override
    public String category() {
        return "Paths";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Path Builder Preferences");
        ImGui.separator();

        ImGui.sliderInt("Default Path Width (Tiles)", defaultPathWidth.getData(), 1, 5);
        ImGui.checkbox("Auto-Smooth Path Corners", autoSmoothCorners);
        ImGui.checkbox("Auto-Blend Edge Transitions", autoBlendEdges);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Path Defaults")) {
            defaultPathWidth.set(1);
            autoSmoothCorners.set(true);
            autoBlendEdges.set(true);
        }
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.PATH + "  Spline Path & Incline Ramp Builder");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("Click tiles sequentially to plot points. Double-click or press Enter to generate path.");
        ImGui.separator();

        if (ImGui.button("Build Flat Road##b-road", 130.0f, 24.0f)) {
            // Build flat road
        }
        ImGui.sameLine(0.0f, 8.0f);
        if (ImGui.button("Build Incline Ramp##b-ramp", 140.0f, 24.0f)) {
            // Build ramp
        }
        ImGui.sameLine(0.0f, 8.0f);
        if (ImGui.button("Clear Plotted Points##b-clear", 140.0f, 24.0f)) {
            // Clear path
        }
    }
}
