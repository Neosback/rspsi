package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Merges compatible static occluder rectangles before camera-space testing.
 *
 * <p>The RuneScape scene builder consumes contiguous occlusion masks and emits
 * larger type-1, type-2, and type-4 planes rather than retaining every source
 * wall edge as a separate occluder. This neutral pass mirrors that behavior
 * for the occluder inputs available to the editor. It is deliberately strict:
 * rectangles may only merge when their plane, height, fixed axis, and span
 * geometry agree, and their tile ranges touch.</p>
 */
public final class SceneOccluderMerger {
    private SceneOccluderMerger() {
    }

    /** Returns deterministic, span-merged occluders without mutating input. */
    public static List<SceneOccluder> merge(List<SceneOccluder> source) {
        Objects.requireNonNull(source, "source");
        List<SceneOccluder> working = new ArrayList<>(source);
        working.sort(Comparator.comparingInt(SceneOccluder::type)
                .thenComparingInt(SceneOccluder::minPlane)
                .thenComparingInt(SceneOccluder::maxPlane)
                .thenComparingInt(SceneOccluder::minHeight)
                .thenComparingInt(SceneOccluder::maxHeight)
                .thenComparingInt(SceneOccluder::minWorldX)
                .thenComparingInt(SceneOccluder::minWorldY)
                .thenComparingInt(SceneOccluder::maxWorldX)
                .thenComparingInt(SceneOccluder::maxWorldY)
                .thenComparingInt(SceneOccluder::minTileX)
                .thenComparingInt(SceneOccluder::minTileY));

        List<SceneOccluder> merged = new ArrayList<>(working.size());
        for (SceneOccluder candidate : working) {
            if (merged.isEmpty()) {
                merged.add(candidate);
                continue;
            }
            int last = merged.size() - 1;
            SceneOccluder combined = tryMerge(merged.get(last), candidate);
            if (combined == null) {
                merged.add(candidate);
            } else {
                merged.set(last, combined);
            }
        }
        merged.sort(Comparator.comparingInt(SceneOccluder::type)
                .thenComparingInt(SceneOccluder::minPlane)
                .thenComparingInt(SceneOccluder::minTileX)
                .thenComparingInt(SceneOccluder::minTileY)
                .thenComparingInt(SceneOccluder::minHeight));
        return List.copyOf(merged);
    }

    private static SceneOccluder tryMerge(SceneOccluder first, SceneOccluder second) {
        if (first.type() != second.type()
                || first.minPlane() != second.minPlane()
                || first.maxPlane() != second.maxPlane()
                || first.minHeight() != second.minHeight()
                || first.maxHeight() != second.maxHeight()) {
            return null;
        }
        return switch (first.type()) {
            case 1 -> mergeXPlane(first, second);
            case 2 -> mergeZPlane(first, second);
            case 4 -> mergeHorizontal(first, second);
            default -> null;
        };
    }

    private static SceneOccluder mergeXPlane(SceneOccluder first, SceneOccluder second) {
        if (first.minWorldX() != first.maxWorldX()
                || second.minWorldX() != second.maxWorldX()
                || first.minWorldX() != second.minWorldX()
                || first.minTileX() != second.minTileX()
                || first.maxTileX() != second.maxTileX()) {
            return null;
        }
        if (!touches(first.minTileY(), first.maxTileY(), second.minTileY(), second.maxTileY())) {
            return null;
        }
        return rectangle(first, Math.min(first.minTileY(), second.minTileY()),
                Math.max(first.maxTileY(), second.maxTileY()), first.minTileX(), first.maxTileX(),
                first.minWorldX(), first.maxWorldX(),
                Math.min(first.minWorldY(), second.minWorldY()),
                Math.max(first.maxWorldY(), second.maxWorldY()));
    }

    private static SceneOccluder mergeZPlane(SceneOccluder first, SceneOccluder second) {
        if (first.minWorldY() != first.maxWorldY()
                || second.minWorldY() != second.maxWorldY()
                || first.minWorldY() != second.minWorldY()
                || first.minTileY() != second.minTileY()
                || first.maxTileY() != second.maxTileY()) {
            return null;
        }
        if (!touches(first.minTileX(), first.maxTileX(), second.minTileX(), second.maxTileX())) {
            return null;
        }
        return rectangle(first, first.minTileY(), first.maxTileY(),
                Math.min(first.minTileX(), second.minTileX()),
                Math.max(first.maxTileX(), second.maxTileX()),
                Math.min(first.minWorldX(), second.minWorldX()),
                Math.max(first.maxWorldX(), second.maxWorldX()),
                first.minWorldY(), first.maxWorldY());
    }

    private static SceneOccluder mergeHorizontal(SceneOccluder first, SceneOccluder second) {
        if (first.minHeight() != first.maxHeight()
                || first.minHeight() != second.minHeight()) {
            return null;
        }
        if (first.minTileY() == second.minTileY() && first.maxTileY() == second.maxTileY()
                && touches(first.minTileX(), first.maxTileX(), second.minTileX(), second.maxTileX())) {
            return rectangle(first, first.minTileY(), first.maxTileY(),
                    Math.min(first.minTileX(), second.minTileX()),
                    Math.max(first.maxTileX(), second.maxTileX()),
                    Math.min(first.minWorldX(), second.minWorldX()),
                    Math.max(first.maxWorldX(), second.maxWorldX()),
                    first.minWorldY(), first.maxWorldY());
        }
        if (first.minTileX() == second.minTileX() && first.maxTileX() == second.maxTileX()
                && touches(first.minTileY(), first.maxTileY(), second.minTileY(), second.maxTileY())) {
            return rectangle(first,
                    Math.min(first.minTileY(), second.minTileY()),
                    Math.max(first.maxTileY(), second.maxTileY()),
                    first.minTileX(), first.maxTileX(),
                    first.minWorldX(), first.maxWorldX(),
                    Math.min(first.minWorldY(), second.minWorldY()),
                    Math.max(first.maxWorldY(), second.maxWorldY()));
        }
        return null;
    }

    private static boolean touches(int firstMin, int firstMax, int secondMin, int secondMax) {
        return firstMin <= secondMax + 1 && secondMin <= firstMax + 1;
    }

    private static SceneOccluder rectangle(SceneOccluder source,
                                            int minTileY, int maxTileY,
                                            int minTileX, int maxTileX,
                                            int minWorldX, int maxWorldX,
                                            int minWorldY, int maxWorldY) {
        return new SceneOccluder(source.type(), minTileX, maxTileX, minTileY, maxTileY,
                source.minPlane(), source.maxPlane(), minWorldX, maxWorldX,
                minWorldY, maxWorldY, source.minHeight(), source.maxHeight());
    }
}
