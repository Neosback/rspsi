package com.rspsi.studio.ui

import com.rspsi.studio.theme.StudioPalette
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import kotlin.math.max
import kotlin.math.min

/**
 * Host-owned viewport Quick Palette for the active map tool.
 *
 * Extensions provide frontend-neutral EditorUiNode content; Studio owns
 * placement, chrome, and native rendering.
 */
class ToolQuickPalette {
    private val renderer = DeclarativeToolUiRenderer()

    fun render(
        context: StudioPanelContext?,
        viewportX: Float,
        viewportY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        val safeContext = context ?: return
        val plugins = safeContext.studioPlugins() ?: return
        val activeToolId = safeContext.activeToolId() ?: return
        val tool = plugins.toolView(activeToolId).orElse(null) ?: return
        val node = tool.quickPaletteNode().orElse(null) ?: return

        val availableWidth = max(MIN_WIDTH, viewportWidth - OFFSET_X - RIGHT_MARGIN)
        val width = min(MAX_WIDTH, availableWidth)
        val x =
            min(
                viewportX + OFFSET_X,
                viewportX + max(0.0f, viewportWidth - width - EDGE_MARGIN),
            )
        val y =
            min(
                viewportY + OFFSET_Y,
                viewportY + max(0.0f, viewportHeight - MIN_BOTTOM_CLEARANCE),
            )

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always)
        ImGui.setNextWindowSize(width, 0.0f, ImGuiCond.Always)
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID())

        ImGui.pushStyleColor(ImGuiCol.Border, StudioPalette.BORDER_STRONG)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 8.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 8.0f)

        if (ImGui.begin("##ActiveToolQuickPalette", FLAGS)) {
            ImGui.textColored(StudioPalette.ACCENT, tool.name())
            ImGui.separator()
            renderer.render(node)
        }
        ImGui.end()

        ImGui.popStyleVar(2)
        ImGui.popStyleColor()
    }

    companion object {
        private const val OFFSET_X = 74.0f
        private const val OFFSET_Y = 20.0f
        private const val MIN_WIDTH = 160.0f
        private const val MAX_WIDTH = 320.0f
        private const val RIGHT_MARGIN = 20.0f
        private const val EDGE_MARGIN = 8.0f
        private const val MIN_BOTTOM_CLEARANCE = 80.0f

        private const val FLAGS =
            ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoSavedSettings or
                ImGuiWindowFlags.AlwaysAutoResize
    }
}
