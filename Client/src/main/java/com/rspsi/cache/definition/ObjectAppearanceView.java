package com.rspsi.cache.definition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional neutral appearance data used by scene builders and inspectors. */
public record ObjectAppearanceView(
        int animationId,
        boolean contouredGround,
        int scaleX,
        int scaleY,
        int scaleZ,
        int offsetX,
        int offsetY,
        int offsetZ,
        Map<Integer, Integer> recolors,
        Map<Integer, Integer> retextures
) {
    public ObjectAppearanceView {
        if (animationId < -1 || scaleX <= 0 || scaleY <= 0 || scaleZ <= 0) {
            throw new IllegalArgumentException("Invalid object appearance values");
        }
        recolors = immutablePairs(recolors);
        retextures = immutablePairs(retextures);
    }

    public static ObjectAppearanceView empty() {
        return new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of());
    }

    /** Builds a stable mapping from parallel cache arrays, ignoring incomplete pairs. */
    public static Map<Integer, Integer> pairs(int[] from, int[] to) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (from == null || to == null) return Map.of();
        int count = Math.min(from.length, to.length);
        for (int index = 0; index < count; index++) {
            // Cache codecs commonly retain -1 sentinel slots for unused
            // recolor/retexture entries. They are not appearance mappings.
            if (from[index] >= 0 && to[index] >= 0) {
                result.put(from[index], to[index]);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Integer> immutablePairs(Map<Integer, Integer> pairs) {
        if (pairs == null) return Map.of();
        for (Map.Entry<Integer, Integer> entry : pairs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getKey() < 0 || entry.getValue() < 0) {
                throw new IllegalArgumentException("Appearance replacements must be non-negative");
            }
        }
        return Map.copyOf(pairs);
    }
}
