package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationCurveView;
import com.rspsi.cache.definition.CachedSkeletalAnimationView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ModelSkeletalSkinView;
import com.rspsi.cache.definition.SkeletalRigView;
import com.rspsi.cache.definition.SkeletonDefinitionView;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the current OSRS cached-model skeletal animation path represented by
 * melxin {@code class146 -> class251/class136 -> Model.method5952}.
 */
public final class CachedSkeletalModelAnimation {
    private CachedSkeletalModelAnimation() {
    }

    public static ModelGeometryView apply(ModelGeometryView geometry,
                                          ModelSkeletalSkinView skin,
                                          SkeletonDefinitionView skeleton,
                                          CachedSkeletalAnimationView animation,
                                          int frame) {
        if (geometry == null || skin == null || skeleton == null || animation == null) {
            return geometry;
        }
        if (geometry.id() != skin.modelId()
                || geometry.vertexCount() != skin.vertexCount()
                || skeleton.id() != animation.skeletonId()) {
            return geometry;
        }

        Optional<SkeletalRigView> rigValue = skeleton.rig();
        if (rigValue.isEmpty()) return geometry;
        SkeletalRigView rig = rigValue.orElseThrow();
        if (animation.poseIndex() >= rig.poseCount()
                || animation.boneCount() != rig.boneCount()) {
            return geometry;
        }

        int boneCount = rig.boneCount();
        float[][] bindLocal = new float[boneCount][];
        float[][] bindCumulative = new float[boneCount][];
        float[][] animatedLocal = new float[boneCount][];
        float[][] animatedCumulative = new float[boneCount][];
        float[][] skinMatrices = new float[boneCount][];

        for (int bone = 0; bone < boneCount; bone++) {
            bindLocal[bone] = rig.bindMatrix(bone, animation.poseIndex());
            animatedLocal[bone] = animatedLocal(
                    bindLocal[bone], animation, bone, frame);
        }
        int[] parents = rig.parentIndices();
        for (int bone = 0; bone < boneCount; bone++) {
            cumulative(bone, parents, bindLocal, bindCumulative);
            cumulative(bone, parents, animatedLocal, animatedCumulative);
            skinMatrices[bone] = multiply(
                    animatedCumulative[bone], inverse(bindCumulative[bone]));
        }

        int[] basePositions = geometry.vertexPositions();
        int[] positions = basePositions.clone();
        boolean changed = false;
        for (int vertex = 0; vertex < geometry.vertexCount(); vertex++) {
            int[] bones = skin.boneIndices(vertex);
            int[] weights = skin.weights(vertex);
            if (bones.length == 0) continue;

            float[] blended = zero();
            boolean influenced = false;
            for (int influence = 0; influence < bones.length; influence++) {
                int bone = bones[influence];
                int weight = weights[influence];
                if (bone < 0 || bone >= boneCount || weight <= 0) continue;
                addScaled(blended, skinMatrices[bone], weight / 255.0f);
                influenced = true;
            }
            if (!influenced) continue;

            int offset = vertex * 3;
            float x = basePositions[offset];
            float y = -basePositions[offset + 1];
            float z = -basePositions[offset + 2];
            int nextX = (int) transformX(blended, x, y, z);
            int nextY = -((int) transformY(blended, x, y, z));
            int nextZ = -((int) transformZ(blended, x, y, z));
            if (nextX != positions[offset]
                    || nextY != positions[offset + 1]
                    || nextZ != positions[offset + 2]) {
                positions[offset] = nextX;
                positions[offset + 1] = nextY;
                positions[offset + 2] = nextZ;
                changed = true;
            }
        }

        int[] alphas = geometry.triangleAlphas();
        int[] triangleSkins = geometry.triangleSkins();
        if (alphas.length > 0 && triangleSkins.length > 0 && animation.hasAlphaTransforms()) {
            int[] types = skeleton.transformTypes();
            int[][] labels = skeleton.labels();
            Map<Integer, java.util.List<Integer>> faces = members(triangleSkins);
            for (int transform = 0; transform < types.length; transform++) {
                if (types[transform] != 5) continue;
                AnimationCurveView curve = animation.alphaCurve(transform);
                if (curve == null) continue;
                int delta = (int) (curve.valueAt(frame) * 255.0f);
                if (delta == 0) continue;
                for (int label : labels[transform]) {
                    var group = faces.get(label & 0xFF);
                    if (group == null) continue;
                    for (int face : group) {
                        int next = Math.max(0, Math.min(255, (alphas[face] & 0xFF) + delta));
                        if (next != (alphas[face] & 0xFF)) {
                            alphas[face] = next;
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed ? geometry.withAnimatedData(positions, alphas) : geometry;
    }

    private static float[] animatedLocal(float[] bind,
                                         CachedSkeletalAnimationView animation,
                                         int bone,
                                         int frame) {
        float[] inverseBind = inverse(bind);
        float[] rotation = defaultRotation(inverseBind);
        float translateX = bind[12];
        float translateY = bind[13];
        float translateZ = bind[14];
        float[] scale = defaultScale(bind);

        AnimationCurveView curve;
        curve = animation.boneCurve(bone, 0);
        if (curve != null) rotation[0] = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 1);
        if (curve != null) rotation[1] = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 2);
        if (curve != null) rotation[2] = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 3);
        if (curve != null) translateX = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 4);
        if (curve != null) translateY = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 5);
        if (curve != null) translateZ = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 6);
        if (curve != null) scale[0] = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 7);
        if (curve != null) scale[1] = curve.valueAt(frame);
        curve = animation.boneCurve(bone, 8);
        if (curve != null) scale[2] = curve.valueAt(frame);

        // class146 composes Z, then X, then Y quaternions by pre-multiplication.
        float[] qz = quaternion(0.0f, 0.0f, 1.0f, rotation[2]);
        float[] qx = quaternion(1.0f, 0.0f, 0.0f, rotation[0]);
        float[] qy = quaternion(0.0f, 1.0f, 0.0f, rotation[1]);
        float[] q = multiplyQuaternion(qy, multiplyQuaternion(qx, qz));
        float[] result = multiply(scale(scale[0], scale[1], scale[2]), rotation(q));
        result[12] = translateX;
        result[13] = translateY;
        result[14] = translateZ;
        return result;
    }

    private static float[] defaultRotation(float[] inverseBind) {
        float x = (float) -Math.asin(inverseBind[6]);
        double cos = Math.cos(x);
        float y;
        float z;
        if (Math.abs(cos) > 0.005D) {
            y = (float) Math.atan2(inverseBind[2] / cos, inverseBind[10] / cos);
            z = (float) Math.atan2(inverseBind[4] / cos, inverseBind[5] / cos);
        } else {
            y = inverseBind[6] < 0.0f
                    ? (float) Math.atan2(inverseBind[1], inverseBind[0])
                    : (float) -Math.atan2(inverseBind[1], inverseBind[0]);
            z = 0.0f;
        }
        return new float[]{x, y, z};
    }

    private static float[] defaultScale(float[] bind) {
        return new float[]{
                length(bind[0], bind[1], bind[2]),
                length(bind[4], bind[5], bind[6]),
                length(bind[8], bind[9], bind[10])
        };
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float[] cumulative(int bone, int[] parents, float[][] local, float[][] result) {
        if (result[bone] != null) return result[bone];
        int parent = parents[bone];
        result[bone] = parent < 0
                ? local[bone].clone()
                : multiply(cumulative(parent, parents, local, result), local[bone]);
        return result[bone];
    }

    /** Column-major 4x4 matrix multiplication. */
    private static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0.0f;
                for (int k = 0; k < 4; k++) {
                    value += left[k * 4 + row] * right[column * 4 + k];
                }
                result[column * 4 + row] = value;
            }
        }
        return result;
    }

    private static float[] inverse(float[] matrix) {
        double[][] augmented = new double[4][8];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                augmented[row][column] = matrix[column * 4 + row];
            }
            augmented[row][row + 4] = 1.0;
        }

        for (int pivot = 0; pivot < 4; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[best][pivot])) best = row;
            }
            if (Math.abs(augmented[best][pivot]) < 1.0e-9) {
                throw new IllegalArgumentException("Singular skeletal bind matrix");
            }
            if (best != pivot) {
                double[] swap = augmented[pivot];
                augmented[pivot] = augmented[best];
                augmented[best] = swap;
            }
            double divisor = augmented[pivot][pivot];
            for (int column = 0; column < 8; column++) augmented[pivot][column] /= divisor;
            for (int row = 0; row < 4; row++) {
                if (row == pivot) continue;
                double factor = augmented[row][pivot];
                if (factor == 0.0) continue;
                for (int column = 0; column < 8; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }

        float[] result = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                result[column * 4 + row] = (float) augmented[row][column + 4];
            }
        }
        return result;
    }

    private static float[] quaternion(float axisX, float axisY, float axisZ, float angle) {
        float sin = (float) Math.sin(angle * 0.5f);
        return new float[]{
                axisX * sin, axisY * sin, axisZ * sin,
                (float) Math.cos(angle * 0.5f)
        };
    }

    /** Standard quaternion product left * right, values stored x,y,z,w. */
    private static float[] multiplyQuaternion(float[] left, float[] right) {
        float lx = left[0], ly = left[1], lz = left[2], lw = left[3];
        float rx = right[0], ry = right[1], rz = right[2], rw = right[3];
        return new float[]{
                lw * rx + lx * rw + ly * rz - lz * ry,
                lw * ry - lx * rz + ly * rw + lz * rx,
                lw * rz + lx * ry - ly * rx + lz * rw,
                lw * rw - lx * rx - ly * ry - lz * rz
        };
    }

    /** Matches TransformationMatrix.method9457's column-major quaternion matrix. */
    private static float[] rotation(float[] q) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float[] result = identity();
        result[0] = w * w + x * x - z * z - y * y;
        result[1] = 2.0f * (w * z + x * y);
        result[2] = 2.0f * (x * z - w * y);
        result[4] = 2.0f * (x * y - w * z);
        result[5] = w * w + y * y - x * x - z * z;
        result[6] = 2.0f * (w * x + y * z);
        result[8] = 2.0f * (w * y + x * z);
        result[9] = 2.0f * (y * z - w * x);
        result[10] = w * w + z * z - y * y - x * x;
        return result;
    }

    private static float[] scale(float x, float y, float z) {
        float[] result = zero();
        result[0] = x;
        result[5] = y;
        result[10] = z;
        result[15] = 1.0f;
        return result;
    }

    private static float[] identity() {
        float[] result = zero();
        result[0] = result[5] = result[10] = result[15] = 1.0f;
        return result;
    }

    private static float[] zero() {
        return new float[16];
    }

    private static void addScaled(float[] target, float[] source, float factor) {
        for (int index = 0; index < 16; index++) target[index] += source[index] * factor;
    }

    private static float transformX(float[] matrix, float x, float y, float z) {
        return matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
    }

    private static float transformY(float[] matrix, float x, float y, float z) {
        return matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
    }

    private static float transformZ(float[] matrix, float x, float y, float z) {
        return matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
    }

    private static Map<Integer, java.util.List<Integer>> members(int[] skins) {
        Map<Integer, java.util.List<Integer>> result = new HashMap<>();
        for (int index = 0; index < skins.length; index++) {
            result.computeIfAbsent(skins[index] & 0xFF,
                    ignored -> new java.util.ArrayList<>()).add(index);
        }
        return result;
    }
}
