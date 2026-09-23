package com.rspsi.cache.definition;

import java.util.Arrays;

/** Per-vertex cached-model bone indices and 0..255 influence weights. */
public record ModelSkeletalSkinView(
        int modelId,
        int[][] boneIndices,
        int[][] weights
) {
    public ModelSkeletalSkinView {
        if (modelId < 0 || boneIndices == null || weights == null
                || boneIndices.length != weights.length) {
            throw new IllegalArgumentException("Invalid model skeletal skin");
        }
        int[][] boneCopy = new int[boneIndices.length][];
        int[][] weightCopy = new int[weights.length][];
        for (int vertex = 0; vertex < boneIndices.length; vertex++) {
            int[] bones = boneIndices[vertex];
            int[] scales = weights[vertex];
            if (bones == null) bones = new int[0];
            if (scales == null) scales = new int[0];
            if (bones.length != scales.length) {
                throw new IllegalArgumentException("Skeletal bone/weight counts differ");
            }
            boneCopy[vertex] = bones.clone();
            weightCopy[vertex] = scales.clone();
            for (int index = 0; index < bones.length; index++) {
                if (bones[index] < 0 || scales[index] < 0 || scales[index] > 255) {
                    throw new IllegalArgumentException("Invalid skeletal influence");
                }
            }
        }
        boneIndices = boneCopy;
        weights = weightCopy;
    }

    public int vertexCount() {
        return boneIndices.length;
    }

    @Override
    public int[][] boneIndices() {
        return deepCopy(boneIndices);
    }

    @Override
    public int[][] weights() {
        return deepCopy(weights);
    }

    public int[] boneIndices(int vertex) {
        return boneIndices[vertex].clone();
    }

    public int[] weights(int vertex) {
        return weights[vertex].clone();
    }

    private static int[][] deepCopy(int[][] values) {
        int[][] copy = new int[values.length][];
        for (int index = 0; index < values.length; index++) copy[index] = values[index].clone();
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelSkeletalSkinView value)) return false;
        return modelId == value.modelId
                && Arrays.deepEquals(boneIndices, value.boneIndices)
                && Arrays.deepEquals(weights, value.weights);
    }

    @Override
    public int hashCode() {
        int result = 31 * modelId + Arrays.deepHashCode(boneIndices);
        return 31 * result + Arrays.deepHashCode(weights);
    }
}
