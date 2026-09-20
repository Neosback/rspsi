package com.rspsi.editor.corpus;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegionSimilarityIndexTest {
    @Test
    void ranksByWeightedFeatureBlocksAndExplainsScores() {
        RegionSimilarityIndex index = new RegionSimilarityIndex();
        index.put(fingerprint(1, Map.of("grass", 10.0, "sand", 1.0), Map.of("wall", 5.0)));
        index.put(fingerprint(2, Map.of("grass", 9.0, "sand", 1.0), Map.of("wall", 4.0)));
        index.put(fingerprint(3, Map.of("lava", 10.0), Map.of("tree", 8.0)));

        var matches = index.similar(1, 2, Map.of(
                "terrain.underlays", 2.0, "objects.kinds", 1.0));

        assertEquals(2, matches.get(0).regionId());
        assertTrue(matches.get(0).score() > matches.get(1).score());
        assertTrue(matches.get(0).blockScore("terrain.underlays") > 0.99);
    }

    private static RegionFingerprint fingerprint(int id,
                                                  Map<String, Double> floors,
                                                  Map<String, Double> objects) {
        return new RegionFingerprint(id, id >> 8, id & 255, Map.of(
                "terrain.underlays", new FeatureBlock("terrain.underlays", floors),
                "objects.kinds", new FeatureBlock("objects.kinds", objects)));
    }
}
