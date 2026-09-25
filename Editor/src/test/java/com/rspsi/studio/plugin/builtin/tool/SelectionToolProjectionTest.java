package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectionToolProjectionTest {

    @Test
    void singleSelectKeepsItsFloatingToolbarOnlyDescriptor() {
        SingleSelectToolPlugin tool = new SingleSelectToolPlugin();

        assertEquals("studio.tool.select.single", SingleSelectToolPlugin.ID);
        assertEquals("selection.single", SingleSelectToolPlugin.ENGINE_TOOL_ID);
        assertEquals(SingleSelectToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("S", tool.shortcut());
        assertEquals(10, tool.railPriority());
        assertEquals(Set.of(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.NONE, tool.brushUiMode());
        assertFalse(tool.hasContextDrawerContent());
    }

    @Test
    void multiSelectKeepsItsFloatingToolbarOnlyDescriptor() {
        MultiSelectToolPlugin tool = new MultiSelectToolPlugin();

        assertEquals("studio.tool.select.multi", MultiSelectToolPlugin.ID);
        assertEquals("selection.multi", MultiSelectToolPlugin.ENGINE_TOOL_ID);
        assertEquals(MultiSelectToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("M", tool.shortcut());
        assertEquals(11, tool.railPriority());
        assertEquals(Set.of(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.NONE, tool.brushUiMode());
        assertFalse(tool.hasContextDrawerContent());
    }
}
