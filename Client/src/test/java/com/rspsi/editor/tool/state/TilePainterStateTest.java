package com.rspsi.editor.tool.state;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TilePainterStateTest {

    @Test
    void stateValidatesCanonicalShapeRotationAndBrushBounds() {
        TilePainterState state = new TilePainterState();
        state.setShape(11);
        state.setRotation(3);
        state.setBrushRadius(64);

        assertEquals(11, state.shape());
        assertEquals(3, state.rotation());
        assertEquals(64, state.brushRadius());
        assertThrows(IllegalArgumentException.class, () -> state.setShape(12));
        assertThrows(IllegalArgumentException.class, () -> state.setRotation(4));
        assertThrows(IllegalArgumentException.class, () -> state.setBrushRadius(65));
    }

    @Test
    void listenersObserveStateChanges() {
        TilePainterState state = new TilePainterState();
        AtomicInteger changes = new AtomicInteger();
        state.addListener(ignored -> changes.incrementAndGet());

        state.setOverlayId(42);
        state.setApplyOverlay(true);
        state.setBrushId("circle");

        assertEquals(3, changes.get());
        assertEquals(42, state.overlayId());
        assertEquals("circle", state.brushId());
    }
}
