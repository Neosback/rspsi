package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.SkeletonDefinitionView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the legacy OSRS vertex-label transform rules to neutral geometry,
 * reproducing melxin {@code Model.transform} exactly.
 *
 * <p>Two client details are load-bearing and easy to lose:</p>
 * <ul>
 *   <li><b>Two-level skin indirection.</b> {@code Skeleton.labels[group]}
 *       contains skin-label ids, not vertex indices. A vertex belongs to the
 *       label when its per-vertex skin value equals the label id
 *       ({@code vertexSkins[vertex] == labels[group][i]}); the same rule with
 *       per-face skins applies to legacy type-5 alpha animation. Treating
 *       label ids as vertex indices only works for dense humanoid skins where
 *       the ids happen to coincide.</li>
 *   <li><b>Composite rotation order.</b> Type 2 rotates about the <em>moving
 *       average pivot</em> (type 0 updates it, and the pivot used is the value
 *       current at the time the rotation executes, after any earlier
 *       translation), applying roll(Z), then pitch(X), then yaw(Y) with the
 *       natural frame-axis component mapping. The rotations are not
 *       commutative.</li>
 * </ul>
 *
 * <p>The client mutates the shared model in place across frames; here the
 * frame is applied to {@code base} (the un-animated geometry by default) so a
 * frame-time advance can be recomputed from the same source instead of
 * compounding drift through chained calls.</p>
 */
public final class ModelAnimation {
    private ModelAnimation() {
    }


    /** Applies the frame on top of {@code geometry} itself. */
    public static ModelGeometryView apply(ModelGeometryView geometry,
                                          AnimationFrameView frame,
                                          SkeletonDefinitionView skeleton) {
        return apply(geometry, frame, skeleton, geometry);
    }

    /**
     * Applies {@code frame} (selected for the same sequence) to {@code base}
     * and returns the result carried by {@code geometry}'s metadata contract.
     */
    public static ModelGeometryView apply(ModelGeometryView geometry,
                                          AnimationFrameView frame,
                                          SkeletonDefinitionView skeleton,
                                          ModelGeometryView base) {
        if (frame == null || skeleton == null || base == null) return geometry;
        int[] baseSkins = base.vertexSkins();
        int[] baseTriangleSkins = base.triangleSkins();
        if (baseSkins.length == 0 && baseTriangleSkins.length == 0) return geometry;

        int[] positions = base.vertexPositions().clone();
        int[] alphas = base.triangleAlphas().clone();

        int[] types = skeleton.transformTypes();
        int[][] labels = skeleton.labels();
        int[] frameIndices = frame.indices();
        int[] xs = frame.translateX();
        int[] ys = frame.translateY();
        int[] zs = frame.translateZ();

        // Skin-label id -> member vertices/faces, the in-memory equivalent of
        // the client's Model.vertexLabels/faceLabelsAlpha inverse tables.
        Map<Integer, List<Integer>> vertexMembers = buildMembers(baseSkins);
        Map<Integer, List<Integer>> faceMembers = buildMembers(baseTriangleSkins);

        int pivotX = 0;
        int pivotY = 0;
        int pivotZ = 0;
        boolean changed = false;

        for (int i = 0; i < frameIndices.length; i++) {
            int group = frameIndices[i];
            if (group < 0 || group >= types.length || group >= labels.length) continue;
            int type = types[group];
            if (type == 0) {
                int sumX = 0;
                int sumY = 0;
                int sumZ = 0;
                int count = 0;
                for (int labelId : labels[group]) {
                    List<Integer> members = vertexMembers.get(labelId & 0xFF);
                    if (members == null) continue;
                    for (int vertex : members) {
                        if (vertex < 0 || vertex * 3 + 2 >= positions.length) continue;
                        int offset = vertex * 3;
                        sumX += positions[offset];
                        sumY += positions[offset + 1];
                        sumZ += positions[offset + 2];
                        count++;
                    }
                }
                if (count > 0) {
                    pivotX = xs[i] + sumX / count;
                    pivotY = ys[i] + sumY / count;
                    pivotZ = zs[i] + sumZ / count;
                } else {
                    pivotX = xs[i];
                    pivotY = ys[i];
                    pivotZ = zs[i];
                }
                continue;
            }
            changed |= type != 5;
            if (type == 5) {
                if (baseTriangleSkins.length == 0) continue;
                for (int labelId : labels[group]) {
                    List<Integer> members = faceMembers.get(labelId & 0xFF);
                    if (members == null) continue;
                    for (int face : members) {
                        if (face < 0 || face >= alphas.length) continue;
                        // melxin Model.transform type 5: alpha += var3 * 8
                        alphas[face] = Math.max(0, Math.min(255, alphas[face] + xs[i] * 8));
                        changed = true;
                    }
                }
                continue;
            }
            for (int labelId : labels[group]) {
                List<Integer> members = vertexMembers.get(labelId & 0xFF);
                if (members == null) continue;
                for (int vertex : members) {
                    if (vertex < 0 || vertex * 3 + 2 >= positions.length) continue;
                    int offset = vertex * 3;
                    int x = positions[offset] - pivotX;
                    int y = positions[offset + 1] - pivotY;
                    int z = positions[offset + 2] - pivotZ;
                    if (type == 1) {
                        x += xs[i];
                        y += ys[i];
                        z += zs[i];
                    } else if (type == 2) {
                        // Client semantics (melxin Model.transform, TSPS
                        // Model.transform0): the rotation components use the
                        // natural frame-axis mapping (X angle from tx, Y from
                        // ty, Z from tz) and are applied roll(Z), pitch(X),
                        // yaw(Y) about the moving average pivot. The steps
                        // are not commutative - the order is load-bearing.
                        int angleX = (xs[i] & 255) * 8;
                        int angleY = (ys[i] & 255) * 8;
                        int angleZ = (zs[i] & 255) * 8;
                        if (angleZ != 0) {
                            int sin = sin(angleZ);
                            int cos = cos(angleZ);
                            int next = (sin * y + cos * x) >> 16;
                            y = (cos * y - sin * x) >> 16;
                            x = next;
                        }
                        if (angleX != 0) {
                            int sin = sin(angleX);
                            int cos = cos(angleX);
                            int next = (cos * y - sin * z) >> 16;
                            z = (sin * y + cos * z) >> 16;
                            y = next;
                        }
                        if (angleY != 0) {
                            int sin = sin(angleY);
                            int cos = cos(angleY);
                            int next = (sin * z + cos * x) >> 16;
                            z = (cos * z - sin * x) >> 16;
                            x = next;
                        }
                    } else if (type == 3) {
                        x = x * xs[i] / 128;
                        y = y * ys[i] / 128;
                        z = z * zs[i] / 128;
                    }
                    positions[offset] = x + pivotX;
                    positions[offset + 1] = y + pivotY;
                    positions[offset + 2] = z + pivotZ;
                }
            }
        }
        if (!changed) return geometry;
        return geometry.withAnimatedData(positions, alphas);
    }

    /**
     * Skin-label id -> member indices. Both skin values and label ids are
     * normalized to the unsigned 0..255 domain the cache serializes them in;
     * cache adapters may hand either side back as a signed byte.
     */
    private static Map<Integer, List<Integer>> buildMembers(int[] skins) {
        Map<Integer, List<Integer>> members = new HashMap<>();
        for (int index = 0; index < skins.length; index++) {
            members.computeIfAbsent(skins[index] & 0xFF, ignored -> new ArrayList<>()).add(index);
        }
        return members;
    }

    /** Fixed-point sine on the client's 0..2047 angle scale. */
    private static int sin(int angle) {
        return (int) (Math.sin(angle * Math.PI / 1024.0) * 65536.0);
    }

    /** Fixed-point cosine on the client's 0..2047 angle scale. */
    private static int cos(int angle) {
        return (int) (Math.cos(angle * Math.PI / 1024.0) * 65536.0);
    }
}
