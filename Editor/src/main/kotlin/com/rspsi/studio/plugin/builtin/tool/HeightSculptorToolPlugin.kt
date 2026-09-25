package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import com.rspsi.studio.ui.panels.HeightToolPanel
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import java.util.EnumSet
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Native Studio projection for the composite Height Sculptor surface.
 *
 * One Studio entry represents the family of neutral terrain-height tools so switching
 * raise/lower/flatten/etc. does not create duplicate chrome or context drawers.
 */
class HeightSculptorToolPlugin : StudioToolPlugin {
    private val defaultRadius = ImInt(1)
    private val defaultStepRate = ImInt(32)
    private val invertMouseWheel = ImBoolean(false)

    override fun id(): String = ID

    override fun name(): String = "Height Sculptor"

    override fun description(): String =
        "Adjust, raise, lower, smooth, and flatten vertex terrain heights with configurable brush falloffs."

    override fun version(): String = "1.0.0"

    override fun author(): String = "OpenRune Team"

    override fun icon(): String = StudioIcons.HEIGHT

    override fun toolId(): String = ENGINE_TOOL_ID

    /**
     * Preserve Java's immutable Set.of contract because callers treat this as descriptor data,
     * not mutable runtime state.
     */
    override fun toolIds(): MutableSet<String> = TOOL_IDS

    override fun shortcut(): String = "H"

    override fun railPriority(): Int = 30

    override fun category(): String = "Terrain"

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
            "${StudioIcons.TUNE}  Height Sculptor Preferences",
        )
        ImGui.separator()

        ImGui.sliderInt("Default Brush Radius", defaultRadius.data, 1, 16)
        ImGui.sliderInt("Default Step Rate", defaultStepRate.data, 8, 128)
        ImGui.checkbox("Invert Mouse Wheel for Height Adjustment", invertMouseWheel)

        ImGui.spacing()
        if (ImGui.button("${StudioIcons.REFRESH}  Reset Height Defaults")) {
            defaultRadius.set(1)
            defaultStepRate.set(32)
            invertMouseWheel.set(false)
        }
    }

    override fun renderContextDrawer(context: StudioPanelContext?) {
        val panel = if (context != null) HeightToolPanel.INSTANCE else null
        if (panel != null) {
            panel.render(context)
        } else {
            ImGui.textDisabled("Height Sculptor console is initializing...")
        }
    }

    companion object {
        const val ID = "studio.tool.height_sculptor"
        const val ENGINE_TOOL_ID = "terrain.raise"

        // Java's Set return type is a mutable platform type to Kotlin even though the
        // original implementation returned Set.of(...). Keep the override JVM-compatible
        // while preserving the original immutable descriptor contract at runtime.
        private val TOOL_IDS: MutableSet<String> =
            Collections.unmodifiableSet(
                LinkedHashSet(
                    listOf(
                        "terrain.raise",
                        "terrain.lower",
                        "terrain.flatten",
                        "terrain.smooth",
                        "terrain.blend",
                        "terrain.terrace",
                        "terrain.ramp",
                    ),
                ),
            )
    }
}
