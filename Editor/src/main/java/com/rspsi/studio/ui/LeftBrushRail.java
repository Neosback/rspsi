package com.rspsi.studio.ui;

import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.ui.hud.BrushSettingsHud;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.function.Consumer;

/**
 * Left brush rail, docked to the left edge of the viewport.
 *
 * <p>This is not a tool switcher - the bottom bar already switches tools,
 * including Tile Painter and Height Sculptor themselves. This rail exists
 * only for the one thing the bottom bar doesn't give quick access to while
 * actually painting: the brush itself. By default it renders nothing at all
 * unless the active tool is one that paints or sculpts with a brush (see
 * {@link #isBrushToolActive}); View &gt; Left Brush Rail overrides that and
 * forces it to stay visible. Its one button opens {@link BrushSettingsHud},
 * the small moveable HUD that owns brush shape/radius/material now
 * (previously duplicated inside the Tile Painter's own bottom-drawer
 * panel).</p>
 */
public final class LeftBrushRail {

    public static final float RAIL_WIDTH = 46.0f;

    private static final int RAIL_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings;

    /**
     * True when the active tool is one this rail cares about - a real brush
     * tool (Tile Painter, Height Sculptor), per {@link StudioToolPlugin#isBrushTool()}.
     * Checked against that explicit capability rather than current TOOL_RAIL
     * surface membership, so a user overriding a non-brush tool's placement
     * onto the rail from the Plugin Manager can never make this rail treat
     * it as a brush tool - the placement override changes where a button
     * appears, not what the tool actually is. Shared between the rail itself
     * and the layout pass that decides whether to reserve screen space for
     * it, so the two never disagree about whether something is showing.
     */
    public static boolean isBrushToolActive(StudioPluginManager plugins, String activeToolId) {
        return plugins != null && plugins.usesSharedBrushSettings(activeToolId);
    }

    /**
     * @param forceVisible the View &gt; Left Brush Rail override - shows the
     *                      rail even when {@link #isBrushToolActive} is false.
     */
    public void render(StudioPanelContext context,
                       float x, float y, float height,
                       Consumer<String> activateTool,
                       String activeToolId,
                       boolean forceVisible) {
        if (!forceVisible && !isBrushToolActive(context.studioPlugins(), activeToolId)) return;

        ImGui.setNextWindowPos(x, y, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowSize(RAIL_WIDTH, height, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleColor(ImGuiCol.WindowBg, StudioPalette.CHROME_BG);
        ImGui.pushStyleColor(ImGuiCol.Border, StudioPalette.BORDER);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4.0f, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6.0f);

        ImGui.begin("LeftBrushRail", RAIL_FLAGS);

        BrushSettingsHud hud = context.studioPlugins() == null ? null
                : context.studioPlugins().plugin(BrushSettingsHud.ID)
                        .filter(BrushSettingsHud.class::isInstance)
                        .map(BrushSettingsHud.class::cast)
                        .orElse(null);
        boolean open = hud != null && hud.isVisible();

        if (open) {
            ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.ACCENT_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.CHROME_BG);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_MUTED);
        }

        ImGui.pushFont(StudioFonts.icon(), 0.0f);
        if (ImGui.button(StudioIcons.BRUSH + "##brush-rail-settings", 38.0f, 38.0f) && hud != null) {
            hud.setVisible(!open);
        }
        ImGui.popFont();
        ImGui.popStyleColor(4);

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Brush Settings" + (open ? " (showing)" : ""));
        }

        ImGui.end();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }
}
