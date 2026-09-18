package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.SkeletonDefinitionView;

/** Applies the legacy OSRS vertex-label transform rules to neutral geometry. */
public final class ModelAnimation {
    private ModelAnimation() {
    }

    public static ModelGeometryView apply(ModelGeometryView geometry,
                                          AnimationFrameView frame,
                                          SkeletonDefinitionView skeleton) {
        int[] skins = geometry.vertexSkins();
        int[] positions = geometry.vertexPositions();
        int[] alphas = geometry.triangleAlphas();
        int[] triangleSkins = geometry.triangleSkins();
        if ((skins.length == 0 && triangleSkins.length == 0)
                || frame == null || skeleton == null) return geometry;
        int[] types = skeleton.transformTypes();
        int[][] labels = skeleton.labels();
        int[] frameIndices = frame.indices();
        int[] xs = frame.translateX();
        int[] ys = frame.translateY();
        int[] zs = frame.translateZ();
        int centerX = 0;
        int centerY = 0;
        int centerZ = 0;
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
                for (int vertex : labels[group]) {
                    if (vertex < 0 || vertex >= positions.length / 3) continue;
                    int offset = vertex * 3;
                    sumX += positions[offset];
                    sumY += positions[offset + 1];
                    sumZ += positions[offset + 2];
                    count++;
                }
                if (count > 0) {
                    centerX = xs[i] + sumX / count;
                    centerY = ys[i] + sumY / count;
                    centerZ = zs[i] + sumZ / count;
                } else {
                    centerX = xs[i]; centerY = ys[i]; centerZ = zs[i];
                }
                continue;
            }
            changed |= type != 5;
            if (type == 5) {
                if (alphas.length == 0 || triangleSkins.length == 0) continue;
                for (int face = 0; face < triangleSkins.length; face++) {
                    if (triangleSkins[face] != group) continue;
                    alphas[face] = Math.max(0, Math.min(255, alphas[face] + xs[i] * 8));
                    changed = true;
                }
                continue;
            }
            for (int vertex : labels[group]) {
                if (vertex < 0 || vertex >= positions.length / 3
                        || vertex >= skins.length || skins[vertex] != group) continue;
                int offset = vertex * 3;
                int x = positions[offset] - centerX;
                int y = positions[offset + 1] - centerY;
                int z = positions[offset + 2] - centerZ;
                if (type == 1) {
                    x += xs[i]; y += ys[i]; z += zs[i];
                } else if (type == 2) {
                    int rz = (xs[i] & 255) * 8;
                    int rx = (ys[i] & 255) * 8;
                    int ry = (zs[i] & 255) * 8;
                    int sin = (int) (Math.sin(rz * Math.PI / 1024.0) * 65536.0);
                    int cos = (int) (Math.cos(rz * Math.PI / 1024.0) * 65536.0);
                    int next = (sin * y + cos * x) >> 16;
                    y = (cos * y - sin * x) >> 16; x = next;
                    sin = (int) (Math.sin(rx * Math.PI / 1024.0) * 65536.0);
                    cos = (int) (Math.cos(rx * Math.PI / 1024.0) * 65536.0);
                    next = (cos * y - sin * z) >> 16;
                    z = (sin * y + cos * z) >> 16; y = next;
                    sin = (int) (Math.sin(ry * Math.PI / 1024.0) * 65536.0);
                    cos = (int) (Math.cos(ry * Math.PI / 1024.0) * 65536.0);
                    next = (sin * z + cos * x) >> 16;
                    z = (cos * z - sin * x) >> 16; x = next;
                } else if (type == 3) {
                    x = x * xs[i] / 128;
                    y = y * ys[i] / 128;
                    z = z * zs[i] / 128;
                }
                positions[offset] = x + centerX;
                positions[offset + 1] = y + centerY;
                positions[offset + 2] = z + centerZ;
            }
        }
        if (!changed) return geometry;
        return geometry.withAnimatedData(positions, alphas);
    }
}
