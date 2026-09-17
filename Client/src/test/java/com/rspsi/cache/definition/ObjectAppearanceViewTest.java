package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectAppearanceViewTest {

    @Test
    void ignoresSentinelAndIncompleteAppearancePairs() {
        assertEquals(Map.of(12, 34), ObjectAppearanceView.pairs(
                new int[]{12, -1, 56}, new int[]{34, 78}));
    }

    @Test
    void keepsReplacementIterationOrderDeterministicForSceneFingerprints() {
        Map<Integer, Integer> pairs = new LinkedHashMap<>();
        pairs.put(10, 20);
        pairs.put(2, 3);

        ObjectAppearanceView view = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, pairs, Map.of());

        assertTrue(view.toString().contains("recolors={2=3, 10=20}"));
    }
}
