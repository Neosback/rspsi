package com.rspsi.osrs.rules.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileFlagRulesTest {

    @Test
    void canonicalMaskValues() {
        assertEquals(0x1, TileFlagRules.BLOCKED);
        assertEquals(0x2, TileFlagRules.BRIDGE);
        assertEquals(0x4, TileFlagRules.UNDER_ROOF);
        assertEquals(0x8, TileFlagRules.VIS_BELOW);
        assertEquals(0x18, TileFlagRules.MINIMAP_HIDDEN);
    }

    @Test
    void testIndividualFlagPredicates() {
        assertFalse(TileFlagRules.isBlocked(0));
        assertTrue(TileFlagRules.isBlocked(TileFlagRules.BLOCKED));
        assertTrue(TileFlagRules.isBlocked(TileFlagRules.BLOCKED | TileFlagRules.BRIDGE));

        assertFalse(TileFlagRules.isBridge(0));
        assertTrue(TileFlagRules.isBridge(TileFlagRules.BRIDGE));

        assertFalse(TileFlagRules.isUnderRoof(0));
        assertTrue(TileFlagRules.isUnderRoof(TileFlagRules.UNDER_ROOF));

        assertFalse(TileFlagRules.isVisibleBelow(0));
        assertTrue(TileFlagRules.isVisibleBelow(TileFlagRules.VIS_BELOW));

        assertFalse(TileFlagRules.hiddenFromMinimap(0));
        assertTrue(TileFlagRules.hiddenFromMinimap(0x8));
        assertTrue(TileFlagRules.hiddenFromMinimap(0x10));
        assertTrue(TileFlagRules.hiddenFromMinimap(0x18));
    }

    @Test
    void testResolveAllCombinations() {
        TileFlagRules.ResolvedTileFlags clean = TileFlagRules.resolve(0);
        assertFalse(clean.blocked());
        assertFalse(clean.bridge());
        assertFalse(clean.underRoof());
        assertFalse(clean.visibleBelow());
        assertFalse(clean.hiddenFromMinimap());

        int combined = TileFlagRules.BLOCKED | TileFlagRules.BRIDGE | TileFlagRules.UNDER_ROOF | 0x8;
        TileFlagRules.ResolvedTileFlags flags = TileFlagRules.resolve(combined);
        assertTrue(flags.blocked());
        assertTrue(flags.bridge());
        assertTrue(flags.underRoof());
        assertTrue(flags.visibleBelow());
        assertTrue(flags.hiddenFromMinimap());
    }
}
