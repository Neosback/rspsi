package com.rspsi.studio.feature

import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import imgui.ImDrawList

/**
 * The Dear ImGui presentation of one built-in Studio feature.
 *
 * Behavior lives in the Client core modules (tools, commands, services); a feature only
 * draws that behavior into Studio chrome. Every hook is optional, so a feature implements
 * just the surfaces it uses. Features are listed explicitly by [StudioFeatureRegistry];
 * Studio has no third-party plugins to discover.
 */
interface StudioFeature {
    /** Stable id. Saved HUD layouts and visibility toggles key on it, so never change one. */
    fun id(): String

    /** Name shown in menus and tooltips. */
    fun name(): String

    /** Material icon glyph (see [StudioIcons]). */
    fun icon(): String = StudioIcons.OBJECT

    /** Draws onto the viewport canvas, e.g. selection outlines or tile markers. */
    fun renderOverlay(drawList: ImDrawList, context: StudioPanelContext) {}

    /** Draws transparent HUD windows over the viewport, e.g. the tile info pill. */
    fun renderHUD(context: StudioPanelContext) {}

    /** Draws interactive floating windows, e.g. the brush settings palette. */
    fun renderFloating(context: StudioPanelContext) {}

    /** Draws this feature's options where a panel embeds them (label left, control right). */
    fun renderSettings(context: StudioPanelContext) {}
}
