package com.rspsi.studio.ui

import com.rspsi.editor.model.WorldLocation
import com.rspsi.editor.model.WorldTile
import com.rspsi.studio.theme.SettingRows
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import java.util.function.Consumer

/**
 * The Go To dialog (Ctrl+G or the status bar): a world tile by X, Y and plane, or a region
 * by id, each with its own button. Enter submits whichever section was edited last.
 *
 * It only chooses a [WorldLocation]; the application decides whether that frames the camera
 * or loads another region.
 */
class GoToLocationDialog {
    private var openRequested = false
    private val x = ImInt(3222)
    private val y = ImInt(3218)
    private val plane = ImInt(0)
    private val regionId = ImInt(12850)

    /** Which section Enter submits: the one edited last. */
    private var regionMode = false

    /** Opens the dialog, prefilled from [current] when known; the plane starts at 0. */
    fun open(current: WorldTile?) {
        openRequested = true
        plane.set(0)
        regionMode = false
        if (current != null) {
            x.set(current.x)
            y.set(current.y)
            regionId.set(((current.x shr 6) shl 8) or (current.y shr 6))
        }
    }

    /** Draws the modal while open; [navigator] receives the chosen location. */
    fun render(navigator: Consumer<WorldLocation>) {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID)
            openRequested = false
        }
        val center = ImGui.getMainViewport().center
        ImGui.setNextWindowPos(center.x, center.y - 120.0f, ImGuiCond.Appearing, 0.5f, 0.5f)
        ImGui.setNextWindowSize(360.0f, 0.0f, ImGuiCond.Appearing)
        if (!ImGui.beginPopupModal(POPUP_ID, null, ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoSavedSettings)) {
            return
        }
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0)
        var go: WorldLocation? = null

        ImGui.textColored(StudioPalette.u32(StudioPalette.ACCENT), "World tile")
        if (SettingRows.beginPlain("goto-tile")) {
            if (SettingRows.inputInt("X", x) || ImGui.isItemActive()) regionMode = false
            if (SettingRows.inputInt("Y", y) || ImGui.isItemActive()) regionMode = false
            if (SettingRows.inputInt("Plane", plane)) {
                plane.set(plane.get().coerceIn(0, 3))
                regionMode = false
            }
            SettingRows.end()
        }
        val tile = WorldLocation.ofTile(x.get(), y.get(), plane.get())
        if (tile != null) {
            ImGui.textDisabled("Region ${tile.regionX()},${tile.regionY()} (id ${tile.regionId()})")
        } else {
            ImGui.textColored(StudioPalette.u32(StudioPalette.WARNING), "X and Y must be 0 to 16383.")
        }
        ImGui.beginDisabled(tile == null)
        if (StudioWidgets.buttonPrimary("Go to tile", -1.0f, BUTTON_HEIGHT)) go = tile
        ImGui.endDisabled()

        ImGui.dummy(1.0f, 6.0f)
        ImGui.separator()
        ImGui.dummy(1.0f, 4.0f)

        ImGui.textColored(StudioPalette.u32(StudioPalette.ACCENT), "Region")
        if (SettingRows.beginPlain("goto-region")) {
            if (SettingRows.inputInt("Region ID", regionId) || ImGui.isItemActive()) regionMode = true
            SettingRows.end()
        }
        val region = WorldLocation.ofRegionId(regionId.get())
        if (region != null) {
            val baseX = region.regionX() shl 6
            val baseY = region.regionY() shl 6
            ImGui.textDisabled(
                "Region ${region.regionX()},${region.regionY()}  ·  tiles $baseX-${baseX + 63}, $baseY-${baseY + 63}")
        } else {
            ImGui.textColored(StudioPalette.u32(StudioPalette.WARNING), "Region ID must be 0 to 65535.")
        }
        ImGui.beginDisabled(region == null)
        if (StudioWidgets.buttonPrimary("Go to region", -1.0f, BUTTON_HEIGHT)) go = region
        ImGui.endDisabled()

        ImGui.dummy(1.0f, 6.0f)
        if (ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)) {
            go = if (regionMode) region else tile
        }
        if (StudioWidgets.buttonGhost("Cancel", -1.0f, 24.0f) || ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            ImGui.closeCurrentPopup()
        } else if (go != null) {
            ImGui.closeCurrentPopup()
            navigator.accept(go)
        }
        ImGui.endPopup()
    }

    private companion object {
        const val POPUP_ID = "Go to location##map-goto"
        const val BUTTON_HEIGHT = 26.0f
    }
}
