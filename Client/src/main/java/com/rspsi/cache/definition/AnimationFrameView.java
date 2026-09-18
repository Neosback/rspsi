package com.rspsi.cache.definition;

/** Immutable decoded OSRS frame transform list. */
public record AnimationFrameView(int id, int skeletonId, int[] indices,
                                 int[] translateX, int[] translateY, int[] translateZ,
                                 boolean showing) {
    public AnimationFrameView {
        if (id < 0 || skeletonId < 0 || indices == null || translateX == null
                || translateY == null || translateZ == null
                || indices.length != translateX.length || indices.length != translateY.length
                || indices.length != translateZ.length) {
            throw new IllegalArgumentException("Invalid animation frame");
        }
        indices = indices.clone();
        translateX = translateX.clone();
        translateY = translateY.clone();
        translateZ = translateZ.clone();
    }

    @Override public int[] indices() { return indices.clone(); }
    @Override public int[] translateX() { return translateX.clone(); }
    @Override public int[] translateY() { return translateY.clone(); }
    @Override public int[] translateZ() { return translateZ.clone(); }
}
