package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * Modal tool plugin for selecting tiles and rectangular marquee regions.
 */
public final class TileSelectionToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.selection";
    public static final String ENGINE_TOOL_ID = "selection.box";

    // Configurable settings
    private final ImBoolean snapToGrid = new ImBoolean(true);
    private final ImBoolean autoClearOnSwitch = new ImBoolean(false);
    private final float[] selectionColor = new float[]{0.23f, 0.51f, 0.96f, 0.40f}; // RGBA

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Tile Selector";
    }

    @Override
    public String description() {
        return "Select individual tiles or drag rectangular marquee areas to inspect and batch-edit terrain attributes.";
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
        return StudioIcons.SELECT;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "S";
    }

    @Override
    public int railPriority() {
        return 10;
    }

    @Override
    public String category() {
        return "Selection";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Selection Preferences");
        ImGui.separator();

        ImGui.checkbox("Snap Marquee to Grid", snapToGrid);
        ImGui.checkbox("Auto-Clear Selection on Tool Switch", autoClearOnSwitch);

        ImGui.spacing();
        ImGui.text("Selection Box Tint Color:");
        ImGui.colorEdit4("##sel-box-color", selectionColor);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Selection Settings")) {
            snapToGrid.set(true);
            autoClearOnSwitch.set(false);
            selectionColor[0] = 0.23f;
            selectionColor[1] = 0.51f;
            selectionColor[2] = 0.96f;
            selectionColor[3] = 0.40f;
        }
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        int selCount = (context.session() != null && context.session().selection() != null)
                ? context.session().selection().selectedCoordinates().size() : 0;

        ImGui.textColored(0xFF38BDF8, StudioIcons.SELECT + "  Selection Inspector");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textColored(0xFF94A3B8, selCount + " tile(s) currently selected.");

        ImGui.sameLine(0.0f, 16.0f);
        if (ImGui.button("Clear Selection##sel-clr-btn") && context.session() != null) {
            context.session().selection().clear();
        }

        ImGui.sameLine(0.0f, 8.0f);
        if (ImGui.button("Fill Overlay on Selection##sel-fill-ovr")) {
            // Context fill overlay
        }

        ImGui.sameLine(0.0f, 8.0f);
        if (ImGui.button("Fill Underlay on Selection##sel-fill-und")) {
            // Context fill underlay
        }

        ImGui.spacing();
        ImGui.textDisabled("Tip: Click to select a single tile. Click and drag across the viewport to marquee-select a rectangular area.");
    }
}
