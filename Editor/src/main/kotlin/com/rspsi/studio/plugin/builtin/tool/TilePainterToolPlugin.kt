package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import com.rspsi.studio.ui.panels.TilePainterPalette
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import java.util.EnumSet

/**
 * Native Studio projection for the composite Tile Painter tool.
 *
 * Engine behavior remains owned by the neutral editor tool. This class contributes
 * Studio-specific presentation metadata, preferences, and the context-drawer surface.
 */
class TilePainterToolPlugin : StudioToolPlugin {
    private val defaultUnderlay = ImInt(0)
    private val defaultOverlay = ImInt(1)
    private val autoApplyOverlay = ImBoolean(true)
    private val showColorPreview = ImBoolean(true)

    override fun id(): String = ID

    override fun name(): String = "Tile Painter"

    override fun description(): String =
        "Paint and blend terrain tiles with textured overlays, shaded underlays, custom shapes, and palette swatches."

    override fun version(): String = "1.0.0"

    override fun author(): String = "OpenRune Team"

    override fun icon(): String = StudioIcons.BRUSH

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "P"

    override fun railPriority(): Int = 20

    override fun category(): String = "Terrain"

    /**
     * Button placement and brush capability are intentionally separate concepts.
     * The tool appears on the bottom bar and brush rail; moving its button later must
     * not change the fact that it uses shared brush settings.
     */
    override fun surfaces(): MutableSet<StudioToolPlugin.ToolSurface> =
        EnumSet.of(
            StudioToolPlugin.ToolSurface.BOTTOM_BAR,
            StudioToolPlugin.ToolSurface.TOOL_RAIL,
        )

    override fun isBrushTool(): Boolean = true

    override fun isConfigurable(): Boolean = true

    override fun renderSettings(context: StudioPanelContext?) {
        ImGui.textColored(
            StudioDrawColors.abgr(0xFF38BDF8.toInt()),
            "${StudioIcons.TUNE}  Tile Painter Preferences",
        )
        ImGui.separator()

        ImGui.inputInt("Default Underlay ID", defaultUnderlay)
        ImGui.inputInt("Default Overlay ID", defaultOverlay)
        ImGui.checkbox("Auto-enable Overlay on Paint", autoApplyOverlay)
        ImGui.checkbox("Show Live Color Swatch Preview", showColorPreview)

        ImGui.spacing()
        if (ImGui.button("${StudioIcons.REFRESH}  Reset Painter Defaults")) {
            defaultUnderlay.set(0)
            defaultOverlay.set(1)
            autoApplyOverlay.set(true)
            showColorPreview.set(true)
        }
    }

    override fun renderContextDrawer(context: StudioPanelContext?) {
        val palette = TilePainterPalette.INSTANCE
        if (palette != null) {
            palette.render(context)
        } else {
            ImGui.textDisabled("Tile Painter Palette is initializing...")
        }
    }

    companion object {
        const val ID = "studio.tool.tile_painter"
        const val ENGINE_TOOL_ID = "terrain.tile-painter"
    }
}
