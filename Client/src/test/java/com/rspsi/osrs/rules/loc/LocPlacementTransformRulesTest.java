package com.rspsi.osrs.rules.loc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocPlacementTransformRulesTest {

    @Test
    void rotationUsesExistingOsrsQuarterTurnConvention() {
        assertEquals(1, LocPlacementRules.rotateOrientation(0, 1));
        assertEquals(3, LocPlacementRules.rotateOrientation(1, 2));
        assertEquals(0, LocPlacementRules.rotateOrientation(1, 3));
    }

    @Test
    void mirrorsStraightAndCornerPlacementOrientationsOnTheirNativeAxes() {
        // Straight wall: west -> east when reflected west/east.
        assertEquals(2, LocPlacementRules.mirrorOrientationX(0, 0));
        // L wall: west+north -> north+east, represented by rotation 1.
        assertEquals(1, LocPlacementRules.mirrorOrientationX(2, 0));
        // North/south reflection is the X reflection followed by 180 degrees.
        assertEquals(0, LocPlacementRules.mirrorOrientationY(0, 0));
        assertEquals(3, LocPlacementRules.mirrorOrientationY(2, 0));
    }
}
