package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;

import java.util.EnumSet;
import java.util.Set;

/**
 * Modal tool for picking exactly one tile at a time for inspection. Lives on the floating
 * tool rail only; the Tile Inspector panel shows what it selects.
 */
public final class SingleSelectToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.select.single";
    public static final String ENGINE_TOOL_ID = "selection.single";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Single Select";
    }

    @Override
    public String description() {
        return "Click a tile to select exactly one tile for inspection.";
    }

    @Override
    public String icon() {
        return StudioIcons.SELECT;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "S";
    }

    @Override
    public int railPriority() {
        return 10;
    }

    @Override
    public String category() {
        return "Selection";
    }

    @Override
    public Set<ToolSurface> surfaces() {
        // The docked Left Tool Rail is brush-only now (Tile Painter, Height
        // Sculptor); selection lives on the floating rail instead.
        return EnumSet.of(ToolSurface.FLOATING_TOOLBAR);
    }

    @Override
    public boolean hasContextDrawerContent() {
        return false;
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        // No drawer content - selection results show in the Tile Inspector panel instead.
    }
}
