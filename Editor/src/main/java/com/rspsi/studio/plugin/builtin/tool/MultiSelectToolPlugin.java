package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;

import java.util.EnumSet;
import java.util.Set;

/**
 * Modal tool for dragging a rectangular marquee over many tiles for inspection. Lives on the
 * floating tool rail only; the Tile Inspector panel shows what it selects.
 */
public final class MultiSelectToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.select.multi";
    public static final String ENGINE_TOOL_ID = "selection.multi";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Multi Select";
    }

    @Override
    public String description() {
        return "Click and drag to marquee-select a rectangular region of tiles for inspection.";
    }

    @Override
    public String icon() {
        return StudioIcons.AREA;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "M";
    }

    @Override
    public int railPriority() {
        return 11;
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
