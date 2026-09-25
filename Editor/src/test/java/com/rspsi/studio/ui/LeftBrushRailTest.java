package com.rspsi.studio.ui;

import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.builtin.tool.HeightSculptorToolPlugin;
import com.rspsi.studio.plugin.builtin.tool.TilePainterToolPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeftBrushRailTest {

    @Test
    void javaStaticSurfaceRemainsCompatible() {
        assertEquals(46.0f, LeftBrushRail.RAIL_WIDTH);
        assertFalse(LeftBrushRail.isBrushToolActive(null, TilePainterToolPlugin.ENGINE_TOOL_ID));
    }

    @Test
    void railVisibilityFollowsDeclaredSharedBrushCapability() {
        StudioPluginManager plugins = new StudioPluginManager();

        assertTrue(LeftBrushRail.isBrushToolActive(
                plugins, TilePainterToolPlugin.ENGINE_TOOL_ID));
        assertTrue(LeftBrushRail.isBrushToolActive(
                plugins, HeightSculptorToolPlugin.ENGINE_TOOL_ID));
        assertFalse(LeftBrushRail.isBrushToolActive(
                plugins, "selection.single"));
    }
}
