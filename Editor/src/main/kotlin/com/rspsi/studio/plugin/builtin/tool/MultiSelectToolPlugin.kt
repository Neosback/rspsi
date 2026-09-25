package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import java.util.EnumSet

/**
 * Native Studio projection for marquee-selecting a rectangular tile region.
 *
 * Like Single Select, this is a selection-mode switcher rather than a brush tool.
 * It therefore stays on the floating selection toolbar and leaves result display
 * to the Tile Inspector instead of creating duplicate drawer UI.
 */
class MultiSelectToolPlugin : StudioToolPlugin {
    override fun id(): String = ID

    override fun name(): String = "Multi Select"

    override fun description(): String =
        "Click and drag to marquee-select a rectangular region of tiles for inspection."

    override fun icon(): String = StudioIcons.AREA

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "M"

    override fun railPriority(): Int = 11

    override fun category(): String = "Selection"

    override fun surfaces(): MutableSet<StudioToolPlugin.ToolSurface> =
        EnumSet.of(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR)

    override fun hasContextDrawerContent(): Boolean = false

    override fun renderContextDrawer(context: StudioPanelContext?) {
        // Intentionally empty: the Tile Inspector owns selection-result presentation.
    }

    companion object {
        const val ID = "studio.tool.select.multi"
        const val ENGINE_TOOL_ID = "selection.multi"
    }
}
