package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Modal tool plugin for placing, selecting, and manipulating 3D scenery objects.
 */
public final class ObjectPlacementToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.objects";
    public static final String ENGINE_TOOL_ID = "object.place";

    // Configurable settings
    private final ImInt defaultRotation = new ImInt(0);
    private final ImBoolean snapToTileCenter = new ImBoolean(true);
    private final ImBoolean autoIncrementRotation = new ImBoolean(false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Object Spawner";
    }

    @Override
    public String description() {
        return "Spawn, translate, rotate, duplicate, and remove interactive 3D map objects and scenery models.";
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
        return StudioIcons.OBJECT;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "O";
    }

    @Override
    public int railPriority() {
        return 50;
    }

    @Override
    public String category() {
        return "Objects";
    }

    @Override
    public java.util.Set<ToolSurface> surfaces() {
        // The docked Left Tool Rail is brush-only now (Tile Painter, Height
        // Sculptor) and the floating rail is a dedicated selection-mode
        // switcher (Single/Multi Select); object placement lives only on
        // the bottom bar, which already covered it.
        return java.util.EnumSet.of(ToolSurface.BOTTOM_BAR);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), StudioIcons.TUNE + "  Object Tool Preferences");
        ImGui.separator();

        ImGui.sliderInt("Default Spawn Rotation", defaultRotation.getData(), 0, 3);
        ImGui.checkbox("Snap to Tile Center", snapToTileCenter);
        ImGui.checkbox("Auto-Increment Rotation on Stamp", autoIncrementRotation);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Object Tool Defaults")) {
            defaultRotation.set(0);
            snapToTileCenter.set(true);
            autoIncrementRotation.set(false);
        }
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), StudioIcons.OBJECT + "  Object Placement Controls");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("Click in the 3D viewport to spawn or manipulate objects. Use the Outliner or Object Viewer for full definitions.");

        ImGui.spacing();
        if (ImGui.button(StudioIcons.ROTATE_RIGHT + " Rotate CW (+90°)##rot-cw", 150.0f, 22.0f)) {
            defaultRotation.set((defaultRotation.get() + 1) % 4);
        }
        ImGui.sameLine(0.0f, 8.0f);
        if (ImGui.button(StudioIcons.ROTATE_LEFT + " Rotate CCW (-90°)##rot-ccw", 150.0f, 22.0f)) {
            defaultRotation.set((defaultRotation.get() + 3) % 4);
        }
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textColored(StudioDrawColors.abgr(0xFFD49B35), "Active Rotation: " + (defaultRotation.get() * 90) + "° (" + defaultRotation.get() + ")");
    }
}
