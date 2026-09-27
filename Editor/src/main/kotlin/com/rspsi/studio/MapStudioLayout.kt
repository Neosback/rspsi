package com.rspsi.studio

import com.rspsi.studio.ui.LeftBrushRail
import com.rspsi.studio.ui.StudioBottomBar
import com.rspsi.studio.ui.WorkspaceTabBar
import imgui.ImGui

/**
 * Screen rectangles of Map Studio's fixed chrome for one frame (UI_WORKSPACE_CONTRACT):
 * menu bar, workspace tabs, optional left brush rail and docked brush panel, viewport,
 * right sidebar, bottom drawer and status bar. Everything derives from the main viewport,
 * so the layout follows DPI and window size with no horizontal scrolling.
 */
internal data class MapStudioLayout(
    val x: Float,
    val y: Float,
    val width: Float,
    val contentY: Float,
    val contentHeight: Float,
    val viewportX: Float,
    val viewportWidth: Float,
    val rightX: Float,
    val rightWidth: Float,
    /** The right sidebar spans viewport plus drawer, so it never looks cut short. */
    val rightHeight: Float,
    val bottomY: Float,
    /** The drawer stops where the right sidebar begins instead of running under it. */
    val bottomWidth: Float,
    val bottomHeight: Float,
) {
    val viewportHeight: Float get() = contentHeight

    companion object {
        const val STATUS_BAR_HEIGHT = 24.0f

        /** Gap below the menu bar so the workspace tab strip never looks tucked under it. */
        private const val MENU_BAR_GAP = 3.0f

        fun compute(
            bottomBar: StudioBottomBar,
            brushRailVisible: Boolean,
            brushDockWidth: Float,
            requestedRightWidth: Float,
        ): MapStudioLayout {
            val main = ImGui.getMainViewport()
            val menuBarHeight = ImGui.getFrameHeight()
            val x = main.posX
            val y = main.posY + menuBarHeight + MENU_BAR_GAP
            val width = maxOf(1.0f, main.sizeX)
            val height = maxOf(1.0f, main.sizeY - menuBarHeight - MENU_BAR_GAP)

            val leftChrome = (if (brushRailVisible) LeftBrushRail.RAIL_WIDTH else 0.0f) + maxOf(0.0f, brushDockWidth)
            val usableWidth = maxOf(1.0f, width - leftChrome)
            // The viewport keeps about half the window; the sidebar takes a third, 320-460 px.
            val minViewport = minOf(500.0f, maxOf(260.0f, usableWidth * 0.48f))
            val responsiveBase = minOf(390.0f, maxOf(320.0f, usableWidth * 0.34f))
            val maxRight = maxOf(260.0f, usableWidth - minViewport)
            val rightWidth = minOf(minOf(460.0f, maxOf(responsiveBase, requestedRightWidth)), maxRight)
            val viewportX = x + leftChrome
            val viewportWidth = maxOf(minViewport, usableWidth - rightWidth)

            val availableHeight = maxOf(100.0f, height - WorkspaceTabBar.HEIGHT - STATUS_BAR_HEIGHT)
            val drawerHeight = bottomBar.currentHeight()
            val contentHeight = maxOf(100.0f, availableHeight - drawerHeight)
            val contentY = y + WorkspaceTabBar.HEIGHT
            return MapStudioLayout(
                x = x,
                y = y,
                width = width,
                contentY = contentY,
                contentHeight = contentHeight,
                viewportX = viewportX,
                viewportWidth = viewportWidth,
                rightX = viewportX + viewportWidth,
                rightWidth = rightWidth,
                rightHeight = availableHeight,
                bottomY = contentY + contentHeight,
                bottomWidth = maxOf(160.0f, width - rightWidth),
                bottomHeight = drawerHeight,
            )
        }
    }
}
