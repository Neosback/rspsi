package com.rspsi.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OsrsCacheIndexLayoutTest {
    @Test
    void keepsModernAnimationAndSkeletonIndexesDistinctFrom317Ordering() {
        assertEquals(0, OsrsCacheIndexLayout.ANIMATIONS);
        assertEquals(1, OsrsCacheIndexLayout.SKELETONS);
        assertEquals(2, OsrsCacheIndexLayout.CONFIGS);
        assertEquals(5, OsrsCacheIndexLayout.MAPS);
        assertEquals(7, OsrsCacheIndexLayout.MODELS);
        assertEquals(8, OsrsCacheIndexLayout.SPRITES);
        assertEquals(9, OsrsCacheIndexLayout.TEXTURES);
    }
}
