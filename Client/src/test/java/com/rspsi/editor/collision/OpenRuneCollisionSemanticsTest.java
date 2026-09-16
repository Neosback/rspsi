package com.rspsi.editor.collision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the focused collision vocabulary ported from OpenRune-Server. */
class OpenRuneCollisionSemanticsTest {
    @Test
    void compositeMovementMasksMatchStepValidator() {
        assertEquals(0x24013e, CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST);
        assertEquals(0x2401e3, CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST);
        assertEquals(0x24018f, CollisionFlag.BLOCK_NORTH_EAST_AND_WEST);
        assertEquals(0x2401f8, CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST);
    }

    @Test
    void compositeRouteMasksMatchOptionalRouteBlockerStrategy() {
        assertEquals(0x4fa40000, CollisionFlag.BLOCK_NORTH_AND_SOUTH_EAST_ROUTE_BLOCKER);
        assertEquals(0x78e40000, CollisionFlag.BLOCK_NORTH_AND_SOUTH_WEST_ROUTE_BLOCKER);
        assertEquals(0x63e40000, CollisionFlag.BLOCK_NORTH_EAST_AND_WEST_ROUTE_BLOCKER);
        assertEquals(0x7e240000, CollisionFlag.BLOCK_SOUTH_EAST_AND_WEST_ROUTE_BLOCKER);
    }
}
