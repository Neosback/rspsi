package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;

import java.util.EnumSet;
import java.util.Set;

/**
 * Modal tool for dragging a rectangular marquee to select every placed object it covers.
 * Lives on the floating tool rail alongside tile selection; the Object Viewer panel
 * previews the first object selected this way.
 *
 * <p>Studio-level presentation id only - drives the same engine {@code selection.box}
 * tool as {@link SingleObjectSelectToolPlugin}, just in MULTI mode.</p>
 */
public final class MultiObjectSelectToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.select.object.multi";
    public static final String ENGINE_TOOL_ID = "selection.object.multi";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Multi Select Objects";
    }

    @Override
    public String description() {
        return "Click and drag to marquee-select every placed object in a rectangular region.";
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
        return "";
    }

    @Override
    public int railPriority() {
        return 13;
    }

    @Override
    public String category() {
        return "Selection";
    }

    @Override
    public Set<ToolSurface> surfaces() {
        return EnumSet.of(ToolSurface.FLOATING_TOOLBAR);
    }

    @Override
    public boolean hasContextDrawerContent() {
        return false;
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        // No drawer content - selection results show in the Object Viewer panel instead.
    }
}
