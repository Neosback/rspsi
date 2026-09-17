package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectAppearanceViewTest {

    @Test
    void ignoresSentinelAndIncompleteAppearancePairs() {
        assertEquals(Map.of(12, 34), ObjectAppearanceView.pairs(
                new int[]{12, -1, 56}, new int[]{34, 78}));
    }
}
