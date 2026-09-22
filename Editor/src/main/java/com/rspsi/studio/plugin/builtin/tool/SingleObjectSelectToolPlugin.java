package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;

import java.util.EnumSet;
import java.util.Set;

/**
 * Modal tool for picking exactly one placed object at a time. Lives on the floating tool
 * rail alongside tile selection; the Object Viewer panel previews what it selects.
 *
 * <p>This is a Studio-level presentation id only - like Single/Multi tile select, it drives
 * the same engine {@code selection.box} tool (already able to target objects instead of
 * tiles), distinguished by mode and target in {@code MapEditorView.activateTool}.</p>
 */
public final class SingleObjectSelectToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.select.object.single";
    public static final String ENGINE_TOOL_ID = "selection.object.single";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Select Object";
    }

    @Override
    public String description() {
        return "Click a placed object to select exactly one for the Object Viewer's preview.";
    }

    @Override
    public String icon() {
        return StudioIcons.OBJECT;
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
        return 12;
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
