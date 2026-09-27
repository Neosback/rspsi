package com.rspsi.studio.ui;

import com.rspsi.studio.feature.StudioFeatureRegistry;
import com.rspsi.studio.feature.tool.HeightSculptorToolUi;
import com.rspsi.studio.feature.tool.TilePainterToolUi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeftBrushRailTest {

    @Test
    void javaStaticSurfaceRemainsCompatible() {
        assertEquals(46.0f, LeftBrushRail.RAIL_WIDTH);
        assertFalse(LeftBrushRail.isBrushToolActive(null, TilePainterToolUi.ENGINE_TOOL_ID));
    }

    @Test
    void railVisibilityFollowsDeclaredSharedBrushCapability() {
        StudioFeatureRegistry plugins = new StudioFeatureRegistry();

        assertTrue(LeftBrushRail.isBrushToolActive(
                plugins, TilePainterToolUi.ENGINE_TOOL_ID));
        assertTrue(LeftBrushRail.isBrushToolActive(
                plugins, HeightSculptorToolUi.ENGINE_TOOL_ID));
        assertFalse(LeftBrushRail.isBrushToolActive(
                plugins, "selection.single"));
    }
}
