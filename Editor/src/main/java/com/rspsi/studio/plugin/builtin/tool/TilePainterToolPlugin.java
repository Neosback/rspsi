package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Modal tool plugin for composite tile painting (Underlays, Overlays, Shapes, Swatches).
 */
public final class TilePainterToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.tile_painter";
    public static final String ENGINE_TOOL_ID = "terrain.tile-painter";

    // Configurable settings
    private final ImInt defaultUnderlay = new ImInt(0);
    private final ImInt defaultOverlay = new ImInt(1);
    private final ImBoolean autoApplyOverlay = new ImBoolean(true);
    private final ImBoolean showColorPreview = new ImBoolean(true);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Tile Painter";
    }

    @Override
    public String description() {
        return "Paint and blend terrain tiles with textured overlays, shaded underlays, custom shapes, and palette swatches.";
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
        return StudioIcons.BRUSH;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "P";
    }

    @Override
    public int railPriority() {
        return 20;
    }

    @Override
    public String category() {
        return "Terrain";
    }

    @Override
    public java.util.Set<ToolSurface> surfaces() {
        // The floating rail is a dedicated selection-mode switcher (Single/
        // Multi Select); Tile Painter is already on the bottom bar and
        // drives the docked brush rail's visibility from TOOL_RAIL.
        return java.util.EnumSet.of(ToolSurface.BOTTOM_BAR, ToolSurface.TOOL_RAIL);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Tile Painter Preferences");
        ImGui.separator();

        ImGui.inputInt("Default Underlay ID", defaultUnderlay);
        ImGui.inputInt("Default Overlay ID", defaultOverlay);
        ImGui.checkbox("Auto-enable Overlay on Paint", autoApplyOverlay);
        ImGui.checkbox("Show Live Color Swatch Preview", showColorPreview);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Painter Defaults")) {
            defaultUnderlay.set(0);
            defaultOverlay.set(1);
            autoApplyOverlay.set(true);
            showColorPreview.set(true);
        }
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        if (TilePainterPalette.INSTANCE != null) {
            TilePainterPalette.INSTANCE.render(context);
        } else {
            ImGui.textDisabled("Tile Painter Palette is initializing...");
        }
    }
}
