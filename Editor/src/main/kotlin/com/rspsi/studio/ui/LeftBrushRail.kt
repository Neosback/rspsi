package com.rspsi.studio.ui

import com.rspsi.studio.plugin.StudioPluginManager
import com.rspsi.studio.theme.StudioFonts
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.ui.hud.BrushSettingsHud
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import java.util.function.Consumer

/**
 * Left brush rail docked to the viewport edge.
 *
 * The bottom bar switches tools. This rail only exposes shared brush settings
 * for tools that explicitly declare that capability.
 */
class LeftBrushRail {
    @Suppress("UNUSED_PARAMETER")
    fun render(
        context: StudioPanelContext,
        x: Float,
        y: Float,
        height: Float,
        activateTool: Consumer<String>?,
        activeToolId: String?,
        forceVisible: Boolean,
    ) {
        if (!forceVisible && !isBrushToolActive(context.studioPlugins(), activeToolId)) {
            return
        }

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always)
        ImGui.setNextWindowSize(RAIL_WIDTH, height, ImGuiCond.Always)
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID())

        ImGui.pushStyleColor(ImGuiCol.WindowBg, StudioPalette.CHROME_BG)
        ImGui.pushStyleColor(ImGuiCol.Border, StudioPalette.BORDER)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4.0f, 8.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 6.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6.0f)

        ImGui.begin("LeftBrushRail", RAIL_FLAGS)

        val hud =
            context
                .studioPlugins()
                ?.plugin(BrushSettingsHud.ID)
                ?.orElse(null) as? BrushSettingsHud
        val open = hud?.isVisible() == true

        if (open) {
            pushButtonColors(
                StudioPalette.ACCENT,
                StudioPalette.ACCENT_HOVER,
                StudioPalette.ACCENT_ACTIVE,
                StudioPalette.TEXT,
            )
        } else {
            pushButtonColors(
                StudioPalette.CHROME_BG,
                StudioPalette.FIELD_HOVER,
                StudioPalette.ACCENT_SOFT,
                StudioPalette.TEXT_MUTED,
            )
        }

        ImGui.pushFont(StudioFonts.icon(), 0.0f)
        if (ImGui.button("${StudioIcons.BRUSH}##brush-rail-settings", 38.0f, 38.0f) && hud != null) {
            hud.setVisible(!open)
        }
        ImGui.popFont()
        ImGui.popStyleColor(4)

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Brush Settings" + if (open) " (showing)" else "")
        }

        ImGui.end()
        ImGui.popStyleVar(3)
        ImGui.popStyleColor(2)
    }

    private fun pushButtonColors(
        button: Int,
        hovered: Int,
        active: Int,
        text: Int,
    ) {
        ImGui.pushStyleColor(ImGuiCol.Button, button)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hovered)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, active)
        ImGui.pushStyleColor(ImGuiCol.Text, text)
    }

    companion object {
        const val RAIL_WIDTH = 46.0f

        private val RAIL_FLAGS =
            ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoScrollbar or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoSavedSettings

        @JvmStatic
        fun isBrushToolActive(
            plugins: StudioPluginManager?,
            activeToolId: String?,
        ): Boolean = plugins?.usesSharedBrushSettings(activeToolId) == true
    }
}
