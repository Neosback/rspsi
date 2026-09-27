package com.rspsi.studio.feature

import com.rspsi.studio.ui.StudioPanel
import com.rspsi.studio.ui.StudioPanelContext
import java.util.EnumSet
import java.util.Optional

/**
 * The Studio chrome for one core editor tool: its button, shortcut hint, bottom-drawer
 * parameters and optional sidebar panel.
 *
 * The tool's behavior is the neutral `EditorTool` a core module registers under
 * [toolId]; this interface never edits the world itself. Activating the button activates
 * that engine tool through the editor host.
 */
interface StudioToolUi : StudioFeature {
    /** A chrome surface a tool's button can appear on. A tool may appear on several. */
    enum class ToolSurface {
        BOTTOM_BAR,
        FLOATING_TOOLBAR,
        TOOL_RAIL,
    }

    /**
     * Where a tool's brush controls live. This is capability, not placement: moving a
     * button never changes which brush UI the tool owns.
     */
    enum class BrushUiMode {
        /** The tool does not use the shared brush system. */
        NONE,

        /** Studio shows the shared Brush Settings rail/panel while the tool is active. */
        SHARED_SETTINGS,

        /** The tool is brush-aware but draws all brush controls in its own context UI. */
        TOOL_OWNED,
    }

    /** Engine tool id this button activates, e.g. `terrain.tile-painter`. */
    fun toolId(): String

    /**
     * Every engine tool this one button represents. A composite UI can switch between
     * several neutral tools while keeping one rail item and one context drawer.
     */
    fun toolIds(): Set<String> = setOf(toolId())

    /** Hotkey hint shown on the button, e.g. "B". */
    fun shortcut(): String

    /** Sort weight on tool strips; lower comes first. */
    fun railPriority(): Int

    /** Draws the tool's parameters in the bottom drawer while it is active. */
    fun renderContextDrawer(context: StudioPanelContext)

    /** Surfaces the button appears on. */
    fun surfaces(): Set<ToolSurface> = EnumSet.allOf(ToolSurface::class.java)

    /**
     * Whether the tool paints or sculpts with a brush. Only brush tools may use the
     * [ToolSurface.TOOL_RAIL], which exists to surface Brush Settings.
     */
    fun isBrushTool(): Boolean = false

    /** Brush UI ownership; brush tools default to the shared Studio brush panel. */
    fun brushUiMode(): BrushUiMode = if (isBrushTool()) BrushUiMode.SHARED_SETTINGS else BrushUiMode.NONE

    /**
     * A right-sidebar panel this tool owns (like RuneLite's `NavigationButton.panel`). It is
     * registered once with the tool, not only while the tool is active.
     */
    fun ownedPanel(): Optional<StudioPanel> = Optional.empty()

    /** False when the tool shows nothing in the bottom drawer, so the drawer can collapse. */
    fun hasContextDrawerContent(): Boolean = true
}
