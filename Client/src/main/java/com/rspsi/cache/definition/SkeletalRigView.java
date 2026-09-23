package com.rspsi.cache.definition;

import java.util.Arrays;

/**
 * Immutable cached-model skeletal bind-pose data embedded after the legacy
 * transform groups in an OSRS skeleton archive.
 */
public record SkeletalRigView(
        int poseCount,
        int[] parentIndices,
        float[][][] bindMatrices
) {
    public SkeletalRigView {
        if (poseCount <= 0 || parentIndices == null || bindMatrices == null
                || parentIndices.length != bindMatrices.length) {
            throw new IllegalArgumentException("Invalid skeletal rig");
        }
        parentIndices = parentIndices.clone();
        float[][][] copy = new float[bindMatrices.length][][];
        for (int bone = 0; bone < bindMatrices.length; bone++) {
            int parent = parentIndices[bone];
            if (parent < -1 || parent >= bindMatrices.length || parent == bone) {
                throw new IllegalArgumentException("Invalid skeletal parent index");
            }
            if (bindMatrices[bone] == null || bindMatrices[bone].length != poseCount) {
                throw new IllegalArgumentException("Invalid skeletal bind-pose count");
            }
            copy[bone] = new float[poseCount][];
            for (int pose = 0; pose < poseCount; pose++) {
                float[] matrix = bindMatrices[bone][pose];
                if (matrix == null || matrix.length != 16) {
                    throw new IllegalArgumentException("Skeletal bind matrices must be 4x4");
                }
                copy[bone][pose] = matrix.clone();
            }
        }
        bindMatrices = copy;
    }

    public int boneCount() {
        return parentIndices.length;
    }

    @Override
    public int[] parentIndices() {
        return parentIndices.clone();
    }

    @Override
    public float[][][] bindMatrices() {
        float[][][] copy = new float[bindMatrices.length][][];
        for (int bone = 0; bone < bindMatrices.length; bone++) {
            copy[bone] = new float[bindMatrices[bone].length][];
            for (int pose = 0; pose < bindMatrices[bone].length; pose++) {
                copy[bone][pose] = bindMatrices[bone][pose].clone();
            }
        }
        return copy;
    }

    public float[] bindMatrix(int bone, int pose) {
        if (bone < 0 || bone >= bindMatrices.length || pose < 0 || pose >= poseCount) {
            throw new IndexOutOfBoundsException("Skeletal bind matrix index");
        }
        return bindMatrices[bone][pose].clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkeletalRigView value)) return false;
        return poseCount == value.poseCount
                && Arrays.equals(parentIndices, value.parentIndices)
                && Arrays.deepEquals(bindMatrices, value.bindMatrices);
    }

    @Override
    public int hashCode() {
        int result = 31 * poseCount + Arrays.hashCode(parentIndices);
        return 31 * result + Arrays.deepHashCode(bindMatrices);
    }
}
