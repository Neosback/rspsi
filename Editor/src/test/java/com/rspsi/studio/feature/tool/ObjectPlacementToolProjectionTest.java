package com.rspsi.studio.feature.tool;

import com.rspsi.studio.feature.StudioToolUi;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPlacementToolProjectionTest {

    @Test
    void objectPlacementPreservesItsDescriptorContract() {
        ObjectPlacementToolUi tool = new ObjectPlacementToolUi();

        assertEquals("studio.tool.objects", ObjectPlacementToolUi.ID);
        assertEquals("object.place", ObjectPlacementToolUi.ENGINE_TOOL_ID);
        assertEquals(ObjectPlacementToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("Object Spawner", tool.name());
        assertEquals("O", tool.shortcut());
        assertEquals(50, tool.railPriority());
    }

    @Test
    void objectPlacementRemainsBottomBarOnlyAndNonBrush() {
        ObjectPlacementToolUi tool = new ObjectPlacementToolUi();

        assertEquals(
                Set.of(StudioToolUi.ToolSurface.BOTTOM_BAR),
                tool.surfaces());
        assertFalse(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.NONE, tool.brushUiMode());
        assertTrue(tool.hasContextDrawerContent());
    }
}
