package com.rspsi.studio.feature.tool;

import com.rspsi.studio.feature.StudioToolUi;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectionToolProjectionTest {

    @Test
    void singleSelectKeepsItsFloatingToolbarOnlyDescriptor() {
        SingleSelectToolUi tool = new SingleSelectToolUi();

        assertEquals("studio.tool.select.single", SingleSelectToolUi.ID);
        assertEquals("selection.single", SingleSelectToolUi.ENGINE_TOOL_ID);
        assertEquals(SingleSelectToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("S", tool.shortcut());
        assertEquals(10, tool.railPriority());
        assertEquals(Set.of(StudioToolUi.ToolSurface.FLOATING_TOOLBAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.NONE, tool.brushUiMode());
        assertFalse(tool.hasContextDrawerContent());
    }

    @Test
    void multiSelectKeepsItsFloatingToolbarOnlyDescriptor() {
        MultiSelectToolUi tool = new MultiSelectToolUi();

        assertEquals("studio.tool.select.multi", MultiSelectToolUi.ID);
        assertEquals("selection.multi", MultiSelectToolUi.ENGINE_TOOL_ID);
        assertEquals(MultiSelectToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("M", tool.shortcut());
        assertEquals(11, tool.railPriority());
        assertEquals(Set.of(StudioToolUi.ToolSurface.FLOATING_TOOLBAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.NONE, tool.brushUiMode());
        assertFalse(tool.hasContextDrawerContent());
    }
}
