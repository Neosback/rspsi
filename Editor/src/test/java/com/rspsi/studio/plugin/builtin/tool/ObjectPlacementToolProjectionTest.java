package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPlacementToolProjectionTest {

    @Test
    void objectPlacementPreservesItsDescriptorContract() {
        ObjectPlacementToolPlugin tool = new ObjectPlacementToolPlugin();

        assertEquals("studio.tool.objects", ObjectPlacementToolPlugin.ID);
        assertEquals("object.place", ObjectPlacementToolPlugin.ENGINE_TOOL_ID);
        assertEquals(ObjectPlacementToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("Object Spawner", tool.name());
        assertEquals("O", tool.shortcut());
        assertEquals(50, tool.railPriority());
        assertEquals("Objects", tool.category());
        assertTrue(tool.isConfigurable());
    }

    @Test
    void objectPlacementRemainsBottomBarOnlyAndNonBrush() {
        ObjectPlacementToolPlugin tool = new ObjectPlacementToolPlugin();

        assertEquals(
                Set.of(StudioToolPlugin.ToolSurface.BOTTOM_BAR),
                tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.NONE, tool.brushUiMode());
        assertTrue(tool.hasContextDrawerContent());
    }
}
