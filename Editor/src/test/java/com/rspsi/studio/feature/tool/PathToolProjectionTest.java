package com.rspsi.studio.feature.tool;

import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.studio.feature.StudioToolUi;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathToolProjectionTest {

    @Test
    void pathProjectionPreservesDescriptorAndOwnership() {
        PathToolUi tool = new PathToolUi();

        assertEquals("studio.tool.path", PathToolUi.ID);
        assertEquals(SplinePathTool.ID, PathToolUi.ENGINE_TOOL_ID);
        assertEquals(PathToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("P", tool.shortcut());
        assertEquals(40, tool.railPriority());
        assertEquals(Set.of(StudioToolUi.ToolSurface.BOTTOM_BAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.NONE, tool.brushUiMode());
        assertTrue(tool.hasContextDrawerContent());
    }
}
