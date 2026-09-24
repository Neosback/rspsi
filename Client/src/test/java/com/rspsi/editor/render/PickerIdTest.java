package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerIdTest {

    @Test
    void encodesAndDecodesTileCoordinatesRoundTrip() {
        int id = PickerId.encode(2, 3234, 3221, PickerId.terrainSlot());

        assertTrue(PickerId.isValid(id));
        assertEquals(3234, PickerId.tileX(id));
        assertEquals(3221, PickerId.tileY(id));
        assertEquals(PickerId.terrainSlot(), PickerId.slot(id));
    }

    @Test
    void planeIsIntentionallyNotPartOfThePackedKey() {
        int plane0 = PickerId.encode(0, 3234, 3221,
                PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));
        int plane3 = PickerId.encode(3, 3234, 3221,
                PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));

        assertEquals(plane0, plane3,
                "plane is resolved by the exact picker so both world axes retain 14 bits");
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
    void fullJagexFourteenBitCoordinateDomainRoundTrips() {
        int id = PickerId.encode(3, 16383, 16383, 4);

        assertEquals(16383, PickerId.tileX(id));
        assertEquals(16383, PickerId.tileY(id));
        assertEquals(4, PickerId.slot(id));
    }

    @Test
    void highRuneLiteMirrorCoordinatesDoNotAliasLowCoordinates() {
        int high = PickerId.encode(0, 13150, 9012, 2);
        int low = PickerId.encode(0, 13150 & 0x1FFF, 9012 & 0x1FFF, 2);

        assertFalse(high == low);
        assertEquals(13150, PickerId.tileX(high));
        assertEquals(9012, PickerId.tileY(high));
    }

    @Test
    void rejectsCoordinatesOutsidePackedJagexDomainInsteadOfAliasing() {
        assertThrows(IllegalArgumentException.class,
                () -> PickerId.encode(0, 16384, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PickerId.encode(0, 0, 16384, 0));
    }
}
