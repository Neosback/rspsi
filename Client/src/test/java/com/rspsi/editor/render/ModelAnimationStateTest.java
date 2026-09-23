package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAnimationStateTest {
    @Test
    void appliesActiveAnimationHeightOffsetWithoutChangingAuthoredPlacement() {
        ModelAnimationState state = ModelAnimationState.selected(
                77, 1, 101, 2, 12, true);

        assertTrue(state.active());
        assertTrue(state.transformed());
        assertEquals(88, state.renderPlacementHeight(100));
    }

    @Test
    void unresolvedSequenceDoesNotShiftPlacementUntilAFrameIsActive() {
        ModelAnimationState state = ModelAnimationState.unresolved(77, 2, 12);

        assertFalse(state.active());
        assertFalse(state.transformed());
        assertEquals(100, state.renderPlacementHeight(100));
    }
}
