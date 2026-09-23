package com.rspsi.cache.definition;

import java.util.Objects;
import java.util.Optional;

/** Immutable OSRS skeleton transform groups plus optional cached-model bone rig. */
public record SkeletonDefinitionView(
        int id,
        int[] transformTypes,
        int[][] labels,
        Optional<SkeletalRigView> rig
) {
    public SkeletonDefinitionView {
        if (id < 0 || transformTypes == null || labels == null
                || transformTypes.length != labels.length) {
            throw new IllegalArgumentException("Invalid skeleton definition");
        }
        transformTypes = transformTypes.clone();
        int[][] copy = new int[labels.length][];
        for (int i = 0; i < labels.length; i++) copy[i] = labels[i].clone();
        labels = copy;
        rig = Objects.requireNonNull(rig, "rig");
    }

    public SkeletonDefinitionView(int id, int[] transformTypes, int[][] labels) {
        this(id, transformTypes, labels, Optional.empty());
    }

    @Override public int[] transformTypes() { return transformTypes.clone(); }
    @Override public int[][] labels() {
        int[][] copy = new int[labels.length][];
        for (int i = 0; i < labels.length; i++) copy[i] = labels[i].clone();
        return copy;
    }
}
