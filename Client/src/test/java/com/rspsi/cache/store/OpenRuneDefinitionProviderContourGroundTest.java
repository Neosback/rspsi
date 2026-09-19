package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client gates contouring on {@code clipType * 65536 >= 0} and passes
 * the scaled value as the partial-contour parameter (ObjectComposition). The
 * provider must reproduce that mapping, including the int-overflow skip.
 */
class OpenRuneDefinitionProviderContourGroundTest {
    @Test
    void sentinelClipTypeLeavesTheModelUncontoured() {
        assertEquals(-1, OpenRuneDefinitionProvider.contourGroundType(-1));
        assertEquals(0, OpenRuneDefinitionProvider.contourGroundParameter(-1));
    }

    @Test
    void zeroClipTypeRequestsFullGroundAttachment() {
        assertEquals(1, OpenRuneDefinitionProvider.contourGroundType(0));
        assertEquals(0, OpenRuneDefinitionProvider.contourGroundParameter(0));
    }

    @Test
    void positiveClipTypeScalesToThePartialContourParameter() {
        assertEquals(1, OpenRuneDefinitionProvider.contourGroundType(1));
        assertEquals(65536, OpenRuneDefinitionProvider.contourGroundParameter(1));
        assertEquals(1, OpenRuneDefinitionProvider.contourGroundType(2));
        assertEquals(131072, OpenRuneDefinitionProvider.contourGroundParameter(2));
    }

    @Test
    void overflowedClipTypeMatchesTheClientSkipGate() {
        // clipType * 65536 wraps negative once clipType exceeds 32767; the
        // client's >= 0 gate then skips contouring entirely.
        assertEquals(-1, OpenRuneDefinitionProvider.contourGroundType(40000));
        assertEquals(-1, OpenRuneDefinitionProvider.contourGroundType(Integer.MIN_VALUE / 65536 + 1));
    }
}
