package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import java.util.EnumSet

/**
 * Native Studio projection for selecting exactly one tile for inspection.
 *
 * Selection tools intentionally live on the floating selection toolbar. The docked
 * left rail is reserved for shared brush settings, and selection results are shown
 * by the Tile Inspector rather than duplicated in the bottom context drawer.
 */
class SingleSelectToolPlugin : StudioToolPlugin {
    override fun id(): String = ID

    override fun name(): String = "Single Select"

    override fun description(): String =
        "Click a tile to select exactly one tile for inspection."

    override fun icon(): String = StudioIcons.SELECT

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "S"

    override fun railPriority(): Int = 10

    override fun category(): String = "Selection"

    override fun surfaces(): MutableSet<StudioToolPlugin.ToolSurface> =
        EnumSet.of(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR)

    override fun hasContextDrawerContent(): Boolean = false

    override fun renderContextDrawer(context: StudioPanelContext?) {
        // Intentionally empty: the Tile Inspector owns selection-result presentation.
    }

    companion object {
        const val ID = "studio.tool.select.single"
        const val ENGINE_TOOL_ID = "selection.single"
    }
}
