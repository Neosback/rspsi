package com.rspsi.studio.feature.tool;

import com.rspsi.studio.feature.StudioToolUi;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerrainToolProjectionTest {

    @Test
    void tilePainterPreservesDescriptorAndBrushCapability() {
        TilePainterToolUi tool = new TilePainterToolUi();

        assertEquals("studio.tool.tile_painter", TilePainterToolUi.ID);
        assertEquals("terrain.tile-painter", TilePainterToolUi.ENGINE_TOOL_ID);
        assertEquals(TilePainterToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("P", tool.shortcut());
        assertEquals(20, tool.railPriority());
        assertTrue(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.SHARED_SETTINGS, tool.brushUiMode());
        assertEquals(
                Set.of(StudioToolUi.ToolSurface.BOTTOM_BAR, StudioToolUi.ToolSurface.TOOL_RAIL),
                tool.surfaces());
    }

    @Test
    void heightSculptorRepresentsTheWholeHeightToolFamily() {
        HeightSculptorToolUi tool = new HeightSculptorToolUi();

        assertEquals("studio.tool.height_sculptor", HeightSculptorToolUi.ID);
        assertEquals("terrain.raise", HeightSculptorToolUi.ENGINE_TOOL_ID);
        assertEquals(HeightSculptorToolUi.ENGINE_TOOL_ID, tool.toolId());
        assertEquals("H", tool.shortcut());
        assertEquals(30, tool.railPriority());
        assertTrue(tool.isBrushTool());
        assertEquals(StudioToolUi.BrushUiMode.SHARED_SETTINGS, tool.brushUiMode());
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
