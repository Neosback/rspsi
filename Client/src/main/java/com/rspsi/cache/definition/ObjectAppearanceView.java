package com.rspsi.cache.definition;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

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
        Map<Integer, Integer> retextures,
        boolean castsShadow,
        boolean occludes,
        boolean mergeNormals,
        boolean nonFlatShading,
        int ambient,
        int contrast,
        int decorDisplacement,
        int contourGroundType,
        int contourGroundParameter,
        boolean modelClipped,
        boolean rotated,
        boolean obstructsGround,
        int clipMask,
        boolean randomizeAnimStart,
        boolean delayAnimationUpdate
) {
    /** Compatibility constructor for the 23-argument appearance view. */
    public ObjectAppearanceView(
            int animationId, boolean contouredGround,
            int scaleX, int scaleY, int scaleZ,
            int offsetX, int offsetY, int offsetZ,
            Map<Integer, Integer> recolors,
            Map<Integer, Integer> retextures,
            boolean castsShadow, boolean occludes,
            boolean mergeNormals, boolean nonFlatShading,
            int ambient, int contrast, int decorDisplacement,
            int contourGroundType, int contourGroundParameter,
            boolean modelClipped, boolean rotated,
            boolean obstructsGround, int clipMask) {
        this(animationId, contouredGround, scaleX, scaleY, scaleZ,
                offsetX, offsetY, offsetZ, recolors, retextures,
                castsShadow, occludes, mergeNormals, nonFlatShading,
                ambient, contrast, decorDisplacement,
                contourGroundType, contourGroundParameter,
                modelClipped, rotated, obstructsGround, clipMask,
                true, false);
    }

    /** Compatibility constructor for the original transform-only view. */
    public ObjectAppearanceView(int animationId, boolean contouredGround,
                                int scaleX, int scaleY, int scaleZ,
                                int offsetX, int offsetY, int offsetZ,
                                Map<Integer, Integer> recolors,
                                Map<Integer, Integer> retextures) {
        this(animationId, contouredGround, scaleX, scaleY, scaleZ,
                offsetX, offsetY, offsetZ, recolors, retextures,
                true, false, false, false, 0, 0, 16,
                contouredGround ? 1 : -1, 0, false, false, false, 0,
                true, false);
    }

    public ObjectAppearanceView {
        if (animationId < -1 || scaleX <= 0 || scaleY <= 0 || scaleZ <= 0
                || decorDisplacement < 0 || contourGroundType < -1
                || clipMask < 0) {
            throw new IllegalArgumentException("Invalid object appearance values");
        }
        recolors = immutablePairs(recolors);
        retextures = immutablePairs(retextures);
    }

    public static ObjectAppearanceView empty() {
        return new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0, true, false);
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
        return immutablePairs(result);
    }

    private static Map<Integer, Integer> immutablePairs(Map<Integer, Integer> pairs) {
        if (pairs == null) return Map.of();
        for (Map.Entry<Integer, Integer> entry : pairs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getKey() < 0 || entry.getValue() < 0) {
                throw new IllegalArgumentException("Appearance replacements must be non-negative");
            }
        }
        Map<Integer, Integer> ordered = new TreeMap<>();
        ordered.putAll(pairs);
        return Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
    }
}
