package com.rspsi.studio.ui

import com.rspsi.editor.model.WorldLocation
import com.rspsi.editor.plugin.EditorPluginHost
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.util.function.Consumer

/**
 * The Ctrl+P command palette: search every registered tool and command, or type a location
 * (any Go To text) to jump there.
 *
 * Tools and commands come from the core modules' registry, so anything a module registers
 * is searchable here without extra wiring.
 */
class CommandPalette {
    private var openRequested = false
    private val query = ImString(128)

    fun open() {
        openRequested = true
        query.clear()
    }

    /**
     * Draws the palette while open. [activateTool] switches tools by id; [navigator] receives
     * a location typed into the search box.
     */
    fun render(editorHost: EditorPluginHost?, activateTool: Consumer<String>, navigator: Consumer<WorldLocation>) {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID)
            openRequested = false
        }
        val center = ImGui.getMainViewport().center
        ImGui.setNextWindowPos(center.x, center.y - 100.0f, ImGuiCond.Appearing, 0.5f, 0.5f)
        ImGui.setNextWindowSize(560.0f, 360.0f, ImGuiCond.Appearing)
        ImGui.pushStyleColor(ImGuiCol.PopupBg, StudioDrawColors.abgr(0xF80E1015.toInt()))
        ImGui.pushStyleColor(ImGuiCol.Border, StudioDrawColors.abgr(0xD0272C38.toInt()))
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 12.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14.0f, 14.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f)
        try {
            if (!ImGui.beginPopupModal(POPUP_ID, null, ImGuiWindowFlags.NoDecoration)) return
            renderContents(editorHost, activateTool, navigator)
            ImGui.endPopup()
        } finally {
            ImGui.popStyleVar(3)
            ImGui.popStyleColor(2)
        }
    }

    private fun renderContents(
        editorHost: EditorPluginHost?,
        activateTool: Consumer<String>,
        navigator: Consumer<WorldLocation>,
    ) {
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 12.0f, 8.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 8.0f)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, StudioDrawColors.abgr(0xFF181A22.toInt()))
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, StudioDrawColors.abgr(0xFF222634.toInt()))
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, StudioDrawColors.abgr(0xFF262B3B.toInt()))
        ImGui.setNextItemWidth(-1.0f)
        ImGui.inputTextWithHint("##cmd-query",
            "${StudioIcons.SEARCH}  Type a tool, command, world tile, or region (e.g. 3222,3218)...",
            query, ImGuiInputTextFlags.None)
        ImGui.popStyleColor(3)
        ImGui.popStyleVar(2)

        ImGui.dummy(1.0f, 4.0f)
        ImGui.separator()
        ImGui.dummy(1.0f, 4.0f)

        val text = query.get().lowercase().trim()
        ImGui.beginChild("palette-results", 0.0f, -36.0f, false)
        ImGui.pushStyleVar(ImGuiStyleVar.SelectableTextAlign, 0.0f, 0.5f)
        val location = WorldLocation.parse(text)
        if (location != null && (
                ImGui.selectable("${StudioIcons.NAVIGATION}  Go to ${location.describe()}##palette-goto", true, 0, 0.0f, ROW_HEIGHT) ||
                    ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)
                )
        ) {
            ImGui.closeCurrentPopup()
            navigator.accept(location)
        }
        if (editorHost == null) {
            ImGui.textDisabled("Editor host unavailable.")
        } else {
            val registry = editorHost.registry()
            var any = false
            for (tool in registry.toolRegistrations()) {
                if (!matches(text, tool.label(), tool.id())) continue
                any = true
                if (ImGui.selectable("${StudioIcons.BRUSH}  ${tool.label()}##tool-${tool.id()}", false, 0, 0.0f, ROW_HEIGHT)) {
                    activateTool.accept(tool.id())
                    ImGui.closeCurrentPopup()
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(tool.id())
            }
            for (command in registry.commandRegistrations()) {
                if (!matches(text, command.label(), command.id())) continue
                any = true
                if (ImGui.selectable("${StudioIcons.TERMINAL}  ${command.label()}##command-${command.id()}", false, 0, 0.0f, ROW_HEIGHT)) {
                    try {
                        editorHost.context().session().execute(registry.createCommand(command.id()))
                    } catch (failure: RuntimeException) {
                        editorHost.context().notifications().error("Command failed", failure.message)
                    }
                    ImGui.closeCurrentPopup()
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(command.id())
            }
            if (!any && location == null) ImGui.textDisabled("No matching tools or commands.")
        }
        ImGui.popStyleVar()
        ImGui.endChild()

        ImGui.separator()
        ImGui.dummy(1.0f, 2.0f)
        ImGui.alignTextToFramePadding()
        ImGui.textDisabled("ESC to close  ·  Enter to run")
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 64.0f)
        if (StudioWidgets.buttonGhost("${StudioIcons.CLOSE} Close", 64.0f, 22.0f) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            ImGui.closeCurrentPopup()
        }
    }

    private fun matches(text: String, label: String, id: String): Boolean =
        text.isBlank() || label.lowercase().contains(text) || id.lowercase().contains(text)

    private companion object {
        const val POPUP_ID = "CommandPaletteModal"
        const val ROW_HEIGHT = 26.0f
    }
}
