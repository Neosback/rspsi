package com.rspsi.studio.ui

import com.rspsi.editor.ui.DockRegion
import java.util.EnumSet

/**
 * A native Dear ImGui panel hosted by Studio chrome (right sidebar or bottom drawer).
 *
 * Panels draw only; edits go through the session in [StudioPanelContext]. Placement rules
 * come from UI_WORKSPACE_CONTRACT.md.
 */
interface StudioPanel {
    fun id(): String

    fun title(): String

    /** Material icon glyph for the sidebar rail. */
    fun icon(): String

    fun preferredRegion(): DockRegion

    fun allowedRegions(): Set<DockRegion> = EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM)

    /** Sort order on the rail; lower comes first. */
    fun order(): Int = 0

    /**
     * Preferred total width when this panel owns the right sidebar. Studio clamps the request
     * so the viewport always keeps a usable minimum.
     */
    fun preferredRightSidebarWidth(): Float = 390.0f

    /**
     * Horizontal scrolling is forbidden by default: panels reflow controls, wrap prose or use
     * structured rows. Opt in only for genuinely wide data, such as a timeline where horizontal
     * position carries meaning.
     */
    fun allowHorizontalScroll(): Boolean = false

    fun render(context: StudioPanelContext)
}
