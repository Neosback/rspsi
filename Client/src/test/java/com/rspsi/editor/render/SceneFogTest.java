package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneFogTest {
    private static final SceneFog.Bounds BOUNDS = new SceneFog.Bounds(
            0.0f, 1280.0f, 0.0f, 1280.0f);

    @Test
    void disabledFogDoesNotChangeThePresentation() {
        assertEquals(0.0f, SceneFog.amount(640.0f, 640.0f, BOUNDS, 0));
    }

    @Test
    void fogStartsAtTheSceneEdgeAndUsesTheSharedDepthFormula() {
        assertEquals(0.0f, SceneFog.amount(640.0f, 640.0f, BOUNDS, 3), 0.0001f);
        assertTrue(SceneFog.amount(0.0f, 640.0f, BOUNDS, 3) > 0.0f);
        assertTrue(SceneFog.amount(64.0f, 640.0f, BOUNDS, 3)
                > SceneFog.amount(256.0f, 640.0f, BOUNDS, 3));
    }
}
