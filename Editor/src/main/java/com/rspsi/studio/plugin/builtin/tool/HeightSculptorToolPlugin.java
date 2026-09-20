package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.panels.HeightToolPanel;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Set;

/**
 * Modal tool plugin for raising, lowering, flattening, and smoothing terrain elevation.
 */
public final class HeightSculptorToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.height_sculptor";
    public static final String ENGINE_TOOL_ID = "terrain.raise";

    // Configurable settings
    private final ImInt defaultRadius = new ImInt(1);
    private final ImInt defaultStepRate = new ImInt(32);
    private final ImBoolean invertMouseWheel = new ImBoolean(false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Height Sculptor";
    }

    @Override
    public String description() {
        return "Adjust, raise, lower, smooth, and flatten vertex terrain heights with configurable brush falloffs.";
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
        return StudioIcons.HEIGHT;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public Set<String> toolIds() {
        return Set.of("terrain.raise", "terrain.lower", "terrain.flatten",
                "terrain.smooth", "terrain.blend", "terrain.terrace");
    }

    @Override
    public String shortcut() {
        return "H";
    }

    @Override
    public int railPriority() {
        return 30;
    }

    @Override
    public String category() {
        return "Terrain";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Height Sculptor Preferences");
        ImGui.separator();

        ImGui.sliderInt("Default Brush Radius", defaultRadius.getData(), 1, 16);
        ImGui.sliderInt("Default Step Rate", defaultStepRate.getData(), 8, 128);
        ImGui.checkbox("Invert Mouse Wheel for Height Adjustment", invertMouseWheel);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Height Defaults")) {
            defaultRadius.set(1);
            defaultStepRate.set(32);
            invertMouseWheel.set(false);
        }
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        var panelOpt = context != null ? HeightToolPanel.INSTANCE : null;
        if (panelOpt != null) {
            panelOpt.render(context);
        } else {
            ImGui.textDisabled("Height Sculptor console is initializing...");
        }
    }
}
