package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.studio.plugin.StudioToolPlugin;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathToolProjectionTest {

    @Test
    void pathProjectionPreservesDescriptorAndOwnership() {
        PathToolPlugin tool = new PathToolPlugin();

        assertEquals("studio.tool.path", PathToolPlugin.ID);
        assertEquals(SplinePathTool.ID, PathToolPlugin.ENGINE_TOOL_ID);
        assertEquals(PathToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("P", tool.shortcut());
        assertEquals(40, tool.railPriority());
        assertEquals("Paths", tool.category());
        assertTrue(tool.isConfigurable());
        assertEquals(Set.of(StudioToolPlugin.ToolSurface.BOTTOM_BAR), tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.NONE, tool.brushUiMode());
        assertTrue(tool.hasContextDrawerContent());
    }
}
