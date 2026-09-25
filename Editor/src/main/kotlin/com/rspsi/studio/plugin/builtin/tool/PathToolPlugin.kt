package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.model.FloorId
import com.rspsi.editor.tool.SplinePathTool
import com.rspsi.editor.tool.spline.SplineBrushStyle
import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import com.rspsi.studio.ui.hud.ViewportHudManager
import com.rspsi.studio.ui.panels.TilePainterPalette
import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.type.ImInt
import java.util.EnumSet

/**
 * Native Studio projection for the stateful Catmull-Rom path authoring tool.
 *
 * The neutral [SplinePathTool] owns control points, spline generation, preview geometry,
 * and atomic map mutations. This class owns Studio-only presentation: preferences, HUD,
 * palette browsing, and context-drawer controls.
 */
class PathToolPlugin : StudioToolPlugin {
    private val defaultPathWidth = ImInt(DEFAULT_PATH_WIDTH)
    private var defaultStyle = SplineBrushStyle.SMOOTH
    private val defaultOverlayId = ImInt(DEFAULT_OVERLAY_ID)
    private val drawerOverlay = ImInt(DEFAULT_OVERLAY_ID)

    override fun id(): String = ID

    override fun name(): String = "Path Builder"

    override fun description(): String =
        "Plot sequential Catmull-Rom spline points to generate smooth cobblestone paths, dirt trails, and incline ramps."

    override fun version(): String = "2.0.0"

    override fun author(): String = "OpenRune Team"

    override fun icon(): String = StudioIcons.PATH

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "P"

    override fun railPriority(): Int = 40

    override fun category(): String = "Paths"

    /**
     * Path Builder is its own discrete bottom-bar activity. Although the underlying tool
     * paints tiles, it owns its path controls and should not inherit the shared brush rail.
     */
    override fun surfaces(): MutableSet<StudioToolPlugin.ToolSurface> =
        EnumSet.of(StudioToolPlugin.ToolSurface.BOTTOM_BAR)

    override fun isConfigurable(): Boolean = true

    override fun renderSettings(context: StudioPanelContext?) {
        ImGui.textColored(ACCENT_COLOR, "${StudioIcons.TUNE}  Path Builder Preferences")
        ImGui.separator()

        ImGui.sliderInt("Default Path Width (Tiles)", defaultPathWidth.data, 1, 8)

        ImGui.alignTextToFramePadding()
        ImGui.text("Default Overlay ID:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(80.0f)
        ImGui.inputInt("##def-ovr", defaultOverlayId)
        ImGui.sameLine()

        val color =
            TilePainterPalette.floorColor(
                context?.cache(),
                defaultOverlayId.get(),
                false,
                DEFAULT_SWATCH_COLOR,
            )
        drawSwatch(ImGui.getWindowDrawList(), color, 1.0f)
        ImGui.dummy(SWATCH_SIZE, SWATCH_SIZE)

        ImGui.spacing()
        if (ImGui.button("${StudioIcons.REFRESH}  Reset Path Defaults")) {
            resetDefaults()
        }
    }

    override fun renderHUD(context: StudioPanelContext?) {
        val safeContext = context ?: return
        if (safeContext.activeToolId() != ENGINE_TOOL_ID) {
            return
        }

        val tool = safeContext.toolController()?.activeTool() as? SplinePathTool ?: return
        val huds = safeContext.huds() ?: return

        val count = tool.path().size()
        val text =
            buildString {
                append("Spline Path  |  Points: ")
                append(count)
                append("  |  W: ")
                append(tool.width())
                append("  |  ")
                append(tool.style().displayName())
                append(if (count >= 2) "  |  [Enter] Build  |  [Esc] Clear" else "  |  Click to plot nodes")
            }

        val width = ImGui.calcTextSize(text).x + HUD_PAD_X * 2.0f
        huds.register(ID, ViewportHudManager.Quadrant.BOTTOM_LEFT, 35)
        val placement = huds.place(ID, width, HUD_HEIGHT) ?: return

        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(
            placement.x(),
            placement.y(),
            placement.x() + width,
            placement.y() + HUD_HEIGHT,
            HUD_BG,
            6.0f,
        )
        draw.addRect(
            placement.x(),
            placement.y(),
            placement.x() + width,
            placement.y() + HUD_HEIGHT,
            HUD_BORDER,
            6.0f,
            0,
            1.0f,
        )
        draw.addText(
            placement.x() + HUD_PAD_X,
            placement.y() + HUD_PAD_Y,
            HUD_TEXT,
            text,
        )
    }

    override fun renderContextDrawer(context: StudioPanelContext?) {
        val safeContext = context ?: return
        val tool = safeContext.toolController()?.activeTool() as? SplinePathTool

        ImGui.textColored(ACCENT_COLOR, "${StudioIcons.PATH}  Catmull-Rom Spline Path Builder")
        ImGui.sameLine(0.0f, 16.0f)
        ImGui.textDisabled(
            "Left-click ground (or Shift+click) to drop nodes. Drag nodes to move. " +
                "Right-click or Alt+click a node to delete.",
        )
        ImGui.separator()

        val pointsCount = tool?.path()?.size() ?: 0
        val currentWidth = tool?.width() ?: defaultPathWidth.get()
        val currentStyle = tool?.style() ?: defaultStyle
        val currentOverlay = tool?.overlayId() ?: defaultOverlayId.get()

        renderWidthControls(tool, currentWidth)
        ImGui.sameLine(0.0f, 20.0f)
        renderOverlayControls(safeContext, tool, currentOverlay)
        ImGui.sameLine(0.0f, 20.0f)
        ImGui.textColored(
            if (pointsCount >= 2) READY_COLOR else MUTED_COLOR,
            "Nodes: $pointsCount" + if (pointsCount < 2) " (min 2 required)" else "",
        )

        ImGui.spacing()
        renderPresetRow(safeContext.cache(), tool)
        ImGui.spacing()
        renderActionRow(tool, pointsCount)
    }

    private fun renderWidthControls(
        tool: SplinePathTool?,
        currentWidth: Int,
    ) {
        ImGui.alignTextToFramePadding()
        ImGui.text("Path Width:")
        ImGui.sameLine()

        if (ImGui.button("${StudioIcons.REMOVE}##w-dec", 24.0f, 22.0f)) {
            tool?.setWidth((currentWidth - 1).coerceAtLeast(1))
        }

        ImGui.sameLine(0.0f, 4.0f)
        ImGui.pushItemWidth(60.0f)
        val width = intArrayOf(currentWidth)
        if (ImGui.sliderInt("##path-w", width, 1, 8)) {
            tool?.setWidth(width[0])
        }
        ImGui.popItemWidth()

        ImGui.sameLine(0.0f, 4.0f)
        if (ImGui.button("${StudioIcons.ADD}##w-inc", 24.0f, 22.0f)) {
            tool?.setWidth((currentWidth + 1).coerceAtMost(16))
        }
    }

    private fun renderOverlayControls(
        context: StudioPanelContext,
        tool: SplinePathTool?,
        currentOverlay: Int,
    ) {
        ImGui.text("Overlay ID:")
        ImGui.sameLine()
        ImGui.pushItemWidth(55.0f)

        drawerOverlay.set(currentOverlay)
        if (
            ImGui.inputInt(
                "##path-overlay",
                drawerOverlay,
                1,
                5,
                ImGuiInputTextFlags.CharsDecimal,
            )
        ) {
            setOverlay(tool, drawerOverlay.get().coerceAtLeast(0))
        }
        ImGui.popItemWidth()

        ImGui.sameLine(0.0f, 6.0f)
        val swatchColor =
            TilePainterPalette.floorColor(
                context.cache(),
                currentOverlay,
                false,
                DEFAULT_SWATCH_COLOR,
            )
        val draw = ImGui.getWindowDrawList()
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        drawSwatch(draw, swatchColor, 1.5f)

        if (ImGui.invisibleButton("##ovr-swatch-btn", SWATCH_SIZE, SWATCH_SIZE)) {
            ImGui.openPopup(OVERLAY_POPUP_ID)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(
                "Overlay #${FloorId.definitionId(currentOverlay)} (Click to browse visual palette)",
            )
        }

        ImGui.sameLine(0.0f, 6.0f)
        if (ImGui.button("${StudioIcons.PALETTE} Browse##path-browse-ovr", 88.0f, 22.0f)) {
            ImGui.openPopup(OVERLAY_POPUP_ID)
        }

        if (ImGui.beginPopup(OVERLAY_POPUP_ID)) {
            ImGui.textColored(
                ACCENT_COLOR,
                "${StudioIcons.PALETTE}  Select Cache Overlay Material",
            )
            ImGui.separator()
            renderOverlayGridPopup(context.cache(), tool)
            ImGui.endPopup()
        }
    }

    private fun renderPresetRow(
        cache: LoadedOsrsCacheSession?,
        tool: SplinePathTool?,
    ) {
        ImGui.alignTextToFramePadding()
        ImGui.textDisabled("Presets:")
        ImGui.sameLine()

        PRESETS.forEachIndexed { index, preset ->
            if (index > 0) {
                ImGui.sameLine(0.0f, 4.0f)
            }
            renderPresetButton(tool, cache, preset.first, preset.second)
        }

        ImGui.sameLine(0.0f, 24.0f)
        ImGui.text("Edge Style:")
        ImGui.sameLine()

        val currentStyle = tool?.style() ?: defaultStyle
        SplineBrushStyle.entries.forEach { style ->
            val active = currentStyle == style
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, STYLE_BUTTON)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, STYLE_BUTTON_HOVER)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, STYLE_BUTTON_ACTIVE)
            }

            if (ImGui.button("${style.displayName()}##btn-style-${style.name}")) {
                if (tool != null) {
                    tool.setStyle(style)
                } else {
                    defaultStyle = style
                }
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(styleTooltip(style))
            }

            if (active) {
                ImGui.popStyleColor(3)
            }
            ImGui.sameLine(0.0f, 6.0f)
        }
        ImGui.newLine()
    }

    /**
     * Exhaustive style description keeps the Studio projection synchronized when a new
     * spline edge mode is added to the neutral tool enum.
     */
    private fun styleTooltip(style: SplineBrushStyle): String =
        when (style) {
            SplineBrushStyle.SMOOTH -> "Autotiled organic paths with smooth curved corners"
            SplineBrushStyle.SOLID -> "Blocky 100% full square tile footprint"
            SplineBrushStyle.WEDGE -> "45° diagonal angled corner wedges"
            SplineBrushStyle.RAMP -> "Smooth height elevation gradient from start to end node"
        }

    private fun renderActionRow(
        tool: SplinePathTool?,
        pointsCount: Int,
    ) {
        val canBuild = tool != null && pointsCount >= 2
        if (!canBuild) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f)
        }

        ImGui.pushStyleColor(ImGuiCol.Button, BUILD_BUTTON)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, BUILD_BUTTON_HOVER)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, BUILD_BUTTON_ACTIVE)

        if (
            ImGui.button(
                "${StudioIcons.CHECK}  Build Path [Enter]##btn-build",
                160.0f,
                26.0f,
            ) &&
            canBuild
        ) {
            tool.buildPath()
        }
        ImGui.popStyleColor(3)

        if (!canBuild) {
            ImGui.popStyleVar()
        }

        ImGui.sameLine(0.0f, 10.0f)
        if (ImGui.button("${StudioIcons.CLOSE}  Clear Points [Esc]##btn-clear", 150.0f, 26.0f)) {
            tool?.clear()
        }
    }

    private fun renderPresetButton(
        tool: SplinePathTool?,
        cache: LoadedOsrsCacheSession?,
        name: String,
        overlayId: Int,
    ) {
        val color =
            TilePainterPalette.floorColor(
                cache,
                overlayId,
                false,
                DEFAULT_SWATCH_COLOR,
            )

        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(x + 4.0f, y + 4.0f, x + 16.0f, y + 16.0f, color, 2.0f)
        draw.addRect(
            x + 4.0f,
            y + 4.0f,
            x + 16.0f,
            y + 16.0f,
            SWATCH_BORDER,
            2.0f,
            0,
            1.0f,
        )

        if (ImGui.button("    $name##preset-$overlayId")) {
            setOverlay(tool, overlayId)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Set overlay to #$overlayId ($name)")
        }
    }

    private fun renderOverlayGridPopup(
        cache: LoadedOsrsCacheSession?,
        tool: SplinePathTool?,
    ) {
        val draw = ImGui.getWindowDrawList()
        for (overlayId in 1..128) {
            if (overlayId > 1 && (overlayId - 1) % OVERLAY_GRID_COLUMNS != 0) {
                ImGui.sameLine(0.0f, OVERLAY_GRID_SPACING)
            }

            val color =
                TilePainterPalette.floorColor(
                    cache,
                    overlayId,
                    false,
                    DEFAULT_SWATCH_COLOR,
                )
            val x = ImGui.getCursorScreenPosX()
            val y = ImGui.getCursorScreenPosY()
            draw.addRectFilled(
                x,
                y,
                x + OVERLAY_GRID_SIZE,
                y + OVERLAY_GRID_SIZE,
                color,
                3.0f,
            )

            val selected = tool?.overlayId() ?: defaultOverlayId.get()
            if (overlayId == selected) {
                draw.addRect(
                    x - 1.0f,
                    y - 1.0f,
                    x + OVERLAY_GRID_SIZE + 1.0f,
                    y + OVERLAY_GRID_SIZE + 1.0f,
                    ACCENT_RAW,
                    3.0f,
                    0,
                    2.0f,
                )
            } else {
                draw.addRect(
                    x,
                    y,
                    x + OVERLAY_GRID_SIZE,
                    y + OVERLAY_GRID_SIZE,
                    GRID_BORDER,
                    3.0f,
                    0,
                    1.0f,
                )
            }

            if (
                ImGui.invisibleButton(
                    "ovr-pop-$overlayId",
                    OVERLAY_GRID_SIZE,
                    OVERLAY_GRID_SIZE,
                )
            ) {
                setOverlay(tool, overlayId)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Overlay #${FloorId.definitionId(overlayId)}")
            }
        }
    }

    private fun drawSwatch(
        draw: ImDrawList,
        color: Int,
        borderWidth: Float,
    ) {
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        draw.addRectFilled(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, color, 4.0f)
        draw.addRect(
            x,
            y,
            x + SWATCH_SIZE,
            y + SWATCH_SIZE,
            SWATCH_BORDER,
            4.0f,
            0,
            borderWidth,
        )
    }

    private fun setOverlay(
        tool: SplinePathTool?,
        overlayId: Int,
    ) {
        tool?.setOverlayId(overlayId)
        defaultOverlayId.set(overlayId)
        drawerOverlay.set(overlayId)
    }

    private fun resetDefaults() {
        defaultPathWidth.set(DEFAULT_PATH_WIDTH)
        defaultStyle = SplineBrushStyle.SMOOTH
        defaultOverlayId.set(DEFAULT_OVERLAY_ID)
        drawerOverlay.set(DEFAULT_OVERLAY_ID)
    }

    companion object {
        const val ID = "studio.tool.path"

        @JvmField
        val ENGINE_TOOL_ID: String = SplinePathTool.ID

        private const val DEFAULT_PATH_WIDTH = 2
        private const val DEFAULT_OVERLAY_ID = 1
        private const val SWATCH_SIZE = 22.0f
        private const val HUD_PAD_X = 12.0f
        private const val HUD_PAD_Y = 5.0f
        private const val HUD_HEIGHT = 24.0f
        private const val OVERLAY_GRID_SIZE = 22.0f
        private const val OVERLAY_GRID_SPACING = 4.0f
        private const val OVERLAY_GRID_COLUMNS = 16
        private const val OVERLAY_POPUP_ID = "path_overlay_palette_popup"

        private const val DEFAULT_SWATCH_COLOR = 0xFF4A4A4A.toInt()
        private const val HUD_BG = 0xDF0F172A.toInt()
        private const val HUD_BORDER = 0xDF38BDF8.toInt()
        private const val HUD_TEXT = 0xFFE2E8F0.toInt()
        private const val READY_COLOR = 0xFF34D399.toInt()
        private const val MUTED_COLOR = 0xFF94A3B8.toInt()
        private const val SWATCH_BORDER = 0xFFFFFFFF.toInt()
        private const val GRID_BORDER = 0xFF334155.toInt()
        private const val ACCENT_RAW = 0xFF38BDF8.toInt()

        private val ACCENT_COLOR = StudioDrawColors.abgr(ACCENT_RAW)
        private val STYLE_BUTTON = StudioDrawColors.abgr(0xFF0284C7.toInt())
        private val STYLE_BUTTON_HOVER = StudioDrawColors.abgr(0xFF0369A1.toInt())
        private val STYLE_BUTTON_ACTIVE = StudioDrawColors.abgr(0xFF075985.toInt())
        private val BUILD_BUTTON = StudioDrawColors.abgr(0xFF16A34A.toInt())
        private val BUILD_BUTTON_HOVER = StudioDrawColors.abgr(0xFF15803D.toInt())
        private val BUILD_BUTTON_ACTIVE = StudioDrawColors.abgr(0xFF166534.toInt())

        private val PRESETS =
            listOf(
                "Cobble" to 10,
                "Dirt" to 28,
                "Grass" to 1,
                "Water" to 12,
                "Wood" to 4,
                "Snow" to 35,
            )
    }
}
