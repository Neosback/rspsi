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
    void materialIdsClampToZeroLikeTheJavaImplementation() {
        TilePainterState state = new TilePainterState();

        state.setUnderlayId(-4);
        state.setOverlayId(-9);

        assertEquals(0, state.underlayId());
        assertEquals(0, state.overlayId());
    }

    @Test
    void brushIdsAreValidatedWithoutChangingRegistryIdentity() {
        TilePainterState state = new TilePainterState();

        state.setBrushId(" third-party brush ");

        assertEquals(" third-party brush ", state.brushId());
        assertThrows(IllegalArgumentException.class, () -> state.setBrushId(null));
        assertThrows(IllegalArgumentException.class, () -> state.setBrushId("   "));
    }

    @Test
    void listenersObserveEveryAcceptedSetterCall() {
        TilePainterState state = new TilePainterState();
        AtomicInteger changes = new AtomicInteger();
        state.addListener(ignored -> changes.incrementAndGet());

        state.setOverlayId(42);
        state.setApplyOverlay(true); // Already true, but setter calls are still signals.
        state.setBrushId("circle");

        assertEquals(3, changes.get());
        assertEquals(42, state.overlayId());
        assertEquals("circle", state.brushId());
    }

    @Test
    void rejectedUpdatesDoNotMutateOrNotify() {
        TilePainterState state = new TilePainterState();
        AtomicInteger changes = new AtomicInteger();
        state.addListener(ignored -> changes.incrementAndGet());

        assertThrows(IllegalArgumentException.class, () -> state.setShape(12));
        assertThrows(IllegalArgumentException.class, () -> state.setRotation(-1));
        assertThrows(IllegalArgumentException.class, () -> state.setBrushRadius(65));

        assertEquals(0, changes.get());
        assertEquals(0, state.shape());
        assertEquals(0, state.rotation());
        assertEquals(0, state.brushRadius());
    }

    @Test
    void listenerRemovalStopsFutureNotifications() {
        TilePainterState state = new TilePainterState();
        AtomicInteger changes = new AtomicInteger();
        TilePainterState.Listener listener = ignored -> changes.incrementAndGet();

        state.addListener(listener);
        state.setHeight(64);
        state.removeListener(listener);
        state.setHeight(128);

        assertEquals(1, changes.get());
        assertEquals(128, state.height());
    }
}
