package com.rspsi.editor.render;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Deterministic draw-order group for one scene tile.
 *
 * <p>Opaque and transparent partitions are kept separately so a GPU backend
 * can preserve the OSRS-style category order while still submitting
 * translucent faces after opaque geometry. The combined {@link #modelIndices}
 * list remains the canonical category order for inspectors and compatibility
 * renderers.</p>
 */
public record SceneLayer(Kind kind, List<Integer> modelIndices,
                         List<Integer> opaqueModelIndices,
                         List<Integer> transparentModelIndices) {
    public enum Kind {
        TERRAIN,
        WALL,
        WALL_DECORATION,
        GROUND_OBJECT,
        GROUND_DECORATION
    }

    public SceneLayer {
        kind = Objects.requireNonNull(kind, "kind");
        modelIndices = List.copyOf(Objects.requireNonNull(modelIndices, "modelIndices"));
        opaqueModelIndices = List.copyOf(Objects.requireNonNull(opaqueModelIndices,
                "opaqueModelIndices"));
        transparentModelIndices = List.copyOf(Objects.requireNonNull(transparentModelIndices,
                "transparentModelIndices"));
        if (modelIndices.stream().anyMatch(index -> index == null || index < 0)) {
            throw new IllegalArgumentException("Scene layer model indices must be non-negative");
        }
        validatePartition(modelIndices, opaqueModelIndices, "opaque");
        validatePartition(modelIndices, transparentModelIndices, "transparent");
        if (opaqueModelIndices.size() + transparentModelIndices.size() != modelIndices.size()) {
            throw new IllegalArgumentException("Scene layer model partitions must cover every model");
        }
        LinkedHashSet<Integer> all = new LinkedHashSet<>(modelIndices);
        LinkedHashSet<Integer> partitions = new LinkedHashSet<>(opaqueModelIndices);
        partitions.addAll(transparentModelIndices);
        if (partitions.size() != modelIndices.size()
                || !partitions.equals(all)) {
            throw new IllegalArgumentException("Scene layer model partitions must be disjoint and complete");
        }
    }

    /** Compatibility constructor before explicit alpha partitions were exposed. */
    public SceneLayer(Kind kind, List<Integer> modelIndices) {
        this(kind, modelIndices, modelIndices, List.of());
    }

    private static void validatePartition(List<Integer> all, List<Integer> partition, String name) {
        if (partition.stream().anyMatch(index -> index == null || index < 0 || !all.contains(index))) {
            throw new IllegalArgumentException("Scene layer " + name + " partition contains an unknown model");
        }
    }
}
