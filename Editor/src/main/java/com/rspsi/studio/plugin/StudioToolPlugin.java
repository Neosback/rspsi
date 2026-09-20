package com.rspsi.studio.plugin;

import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Contract for modal action tools docked to the Left Tool Rail (TOOL_RAIL slot).
 *
 * Implements the declarative UI slot system:
 * - Docks exclusively onto the Left Tool Rail.
 * - Captures mouse input while active (Tool Mutex).
 * - Contextually renders its parameters into the Bottom Drawer (BOTTOM_DRAWER slot).
 * - Exposes configurable preferences to the RuneLite-style Settings Inspector.
 */
public interface StudioToolPlugin extends StudioPlugin {

    /** A chrome surface a tool's button can appear on. A tool may appear on more than one. */
    enum ToolSurface {
        BOTTOM_BAR,
        FLOATING_TOOLBAR,
        TOOL_RAIL
    }

    /**
     * Unique identifier for the engine or studio tool (e.g. "terrain.tile-painter").
     */
    String toolId();

    /**
     * Single-character or modifier hotkey hint (e.g. "P", "H", "S", "O", "B").
     */
    String shortcut();

    /**
     * Priority weight on the Left Tool Rail (lower numbers appear higher on the rail).
     */
    int railPriority();

    /**
     * Category name for grouping and filtering.
     */
    default String category() {
        return "Tools";
    }

    /**
     * Renders contextual editing parameters inside the Bottom Drawer when this tool is active.
     */
    void renderContextDrawer(StudioPanelContext context);

    /**
     * Which chrome surfaces this tool's button appears on by default. Defaults to all three so
     * existing tools are unaffected; a tool meant for only one or two surfaces overrides this.
     * The Plugin Manager can override this per-tool at runtime (see
     * {@code StudioPluginManager#effectiveSurfaces}) - this default is only the starting point.
     */
    default Set<ToolSurface> surfaces() {
        return EnumSet.allOf(ToolSurface.class);
    }

    /**
     * Whether this tool's button should appear in the bottom activity bar. Convenience view
     * onto {@link #surfaces()} - kept because most callers only ever care about this one surface.
     */
    default boolean showInBottomBar() {
        return surfaces().contains(ToolSurface.BOTTOM_BAR);
    }

    /**
     * A panel this tool owns and contributes to the right sidebar, analogous to RuneLite's
     * {@code NavigationButton.panel}: a plugin that needs its own persistent settings/detail
     * surface returns it here instead of hand-rolling registration elsewhere. Registered once
     * when the tool plugin is registered (see {@code StudioPluginManager#register}), not
     * conditionally shown only while the tool is active - the same way every other right-sidebar
     * panel behaves.
     */
    default Optional<StudioPanel> ownedPanel() {
        return Optional.empty();
    }

    /**
     * Whether this tool has anything to show in the Bottom Drawer. Tools whose UI lives
     * entirely on another surface (e.g. a side panel) return false so the drawer auto-collapses
     * instead of showing an empty shelf when they become active.
     */
    default boolean hasContextDrawerContent() {
        return true;
    }

    /**
     * Optional transparent HUD or overlay rendered directly on top of the 3D viewport canvas.
     */
    default void renderViewportOverlay(ImDrawList drawList, StudioPanelContext context) {
    }
}
