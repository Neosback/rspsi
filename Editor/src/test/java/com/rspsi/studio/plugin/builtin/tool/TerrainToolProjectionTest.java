package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.studio.plugin.StudioToolPlugin;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerrainToolProjectionTest {

    @Test
    void tilePainterPreservesDescriptorAndBrushCapability() {
        TilePainterToolPlugin tool = new TilePainterToolPlugin();

        assertEquals("studio.tool.tile_painter", TilePainterToolPlugin.ID);
        assertEquals("terrain.tile-painter", TilePainterToolPlugin.ENGINE_TOOL_ID);
        assertEquals(TilePainterToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("P", tool.shortcut());
        assertEquals(20, tool.railPriority());
        assertTrue(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.SHARED_SETTINGS, tool.brushUiMode());
        assertEquals(
                Set.of(StudioToolPlugin.ToolSurface.BOTTOM_BAR, StudioToolPlugin.ToolSurface.TOOL_RAIL),
                tool.surfaces());
    }

    @Test
    void heightSculptorRepresentsTheWholeHeightToolFamily() {
        HeightSculptorToolPlugin tool = new HeightSculptorToolPlugin();

        assertEquals("studio.tool.height_sculptor", HeightSculptorToolPlugin.ID);
        assertEquals("terrain.raise", HeightSculptorToolPlugin.ENGINE_TOOL_ID);
        assertEquals(HeightSculptorToolPlugin.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("H", tool.shortcut());
        assertEquals(30, tool.railPriority());
        assertTrue(tool.isBrushTool());
        assertEquals(StudioToolPlugin.BrushUiMode.SHARED_SETTINGS, tool.brushUiMode());
        assertEquals(
                Set.of(
                        "terrain.raise",
                        "terrain.lower",
                        "terrain.flatten",
                        "terrain.smooth",
                        "terrain.blend",
                        "terrain.terrace",
                        "terrain.ramp"),
                tool.toolIds());
        assertThrows(UnsupportedOperationException.class,
                () -> tool.toolIds().add("terrain.invalid"));
    }
}
