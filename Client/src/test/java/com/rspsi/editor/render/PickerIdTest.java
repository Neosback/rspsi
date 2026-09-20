package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerIdTest {

    @Test
    void encodesAndDecodesTileCoordinatesRoundTrip() {
        int id = PickerId.encode(2, 3234, 3221, PickerId.terrainSlot());

        assertTrue(PickerId.isValid(id));
        assertEquals(2, PickerId.plane(id));
        assertEquals(3234, PickerId.tileX(id));
        assertEquals(3221, PickerId.tileY(id));
        assertEquals(PickerId.terrainSlot(), PickerId.slot(id));
    }

    @Test
    void encodesEveryLayerKindIntoADistinctSlot() {
        for (SceneLayer.Kind kind : SceneLayer.Kind.values()) {
            int id = PickerId.encode(0, 1, 1, PickerId.slotFor(kind));
            assertEquals(kind.ordinal(), PickerId.slot(id));
        }
    }

    @Test
    void zeroIsNotAValidPickerId() {
        assertFalse(PickerId.isValid(PickerId.INVALID));
    }

    @Test
    void maxCoordinatesWithinThirteenBitsRoundTrip() {
        int id = PickerId.encode(3, 8191, 8191, 4);

        assertEquals(3, PickerId.plane(id));
        assertEquals(8191, PickerId.tileX(id));
        assertEquals(8191, PickerId.tileY(id));
        assertEquals(4, PickerId.slot(id));
    }
}
