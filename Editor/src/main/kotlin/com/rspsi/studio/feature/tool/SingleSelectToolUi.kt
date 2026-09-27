package com.rspsi.studio.feature.tool

import com.rspsi.studio.feature.StudioToolUi
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
class SingleSelectToolUi : StudioToolUi {
    override fun id(): String = ID

    override fun name(): String = "Single Select"

    override fun icon(): String = StudioIcons.SELECT

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "S"

    override fun railPriority(): Int = 10

    override fun surfaces(): MutableSet<StudioToolUi.ToolSurface> =
        EnumSet.of(StudioToolUi.ToolSurface.FLOATING_TOOLBAR)

    override fun hasContextDrawerContent(): Boolean = false

    override fun renderContextDrawer(context: StudioPanelContext) {
        // Intentionally empty: the Tile Inspector owns selection-result presentation.
    }

    companion object {
        const val ID = "studio.tool.select.single"
        const val ENGINE_TOOL_ID = "selection.single"
    }
}
