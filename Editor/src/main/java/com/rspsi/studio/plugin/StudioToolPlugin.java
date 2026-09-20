package com.rspsi.studio.plugin;

import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;

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
     * Optional transparent HUD or overlay rendered directly on top of the 3D viewport canvas.
     */
    default void renderViewportOverlay(ImDrawList drawList, StudioPanelContext context) {
    }
}
