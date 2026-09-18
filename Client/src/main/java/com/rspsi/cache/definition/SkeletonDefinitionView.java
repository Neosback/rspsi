package com.rspsi.cache.definition;

/** Immutable OSRS skeleton transform types and vertex-label groups. */
public record SkeletonDefinitionView(int id, int[] transformTypes, int[][] labels) {
    public SkeletonDefinitionView {
        if (id < 0 || transformTypes == null || labels == null
                || transformTypes.length != labels.length) {
            throw new IllegalArgumentException("Invalid skeleton definition");
        }
        transformTypes = transformTypes.clone();
        int[][] copy = new int[labels.length][];
        for (int i = 0; i < labels.length; i++) copy[i] = labels[i].clone();
        labels = copy;
    }

    @Override public int[] transformTypes() { return transformTypes.clone(); }
    @Override public int[][] labels() {
        int[][] copy = new int[labels.length][];
        for (int i = 0; i < labels.length; i++) copy[i] = labels[i].clone();
        return copy;
    }
}
