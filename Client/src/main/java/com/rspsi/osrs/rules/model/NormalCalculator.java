package com.rspsi.osrs.rules.model;

import com.rspsi.osrs.rules.model.ModelTransformPipeline.TransformedVertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formal OSRS face normal and accumulated vertex normal calculator.
 */
public final class NormalCalculator {

    public record ModelNormal(int x, int y, int z, int magnitude) {
        public static final ModelNormal ZERO = new ModelNormal(0, 0, 0, 0);

        public ModelNormal add(ModelNormal other) {
            return new ModelNormal(x + other.x, y + other.y, z + other.z, magnitude + other.magnitude);
        }
    }

    private NormalCalculator() {}

    /**
     * Calculates the face normal vector for 3 vertices using cross-product and 256-scale normalization.
     */
    public static ModelNormal faceNormal(
            int x0, int y0, int z0,
            int x1, int y1, int z1,
            int x2, int y2, int z2
    ) {
        int dx1 = x1 - x0;
        int dy1 = y1 - y0;
        int dz1 = z1 - z0;
        int dx2 = x2 - x0;
        int dy2 = y2 - y0;
        int dz2 = z2 - z0;

        int nx = dy1 * dz2 - dy2 * dz1;
        int ny = dz1 * dx2 - dz2 * dx1;
        int nz = dx1 * dy2 - dx2 * dy1;

        while (Math.abs(nx) > 8192 || Math.abs(ny) > 8192 || Math.abs(nz) > 8192) {
            nx >>= 1;
            ny >>= 1;
            nz >>= 1;
        }

        int magnitude = Math.max(1, (int) Math.sqrt((long) nx * nx + (long) ny * ny + (long) nz * nz));
        return new ModelNormal(nx * 256 / magnitude, ny * 256 / magnitude, nz * 256 / magnitude, 1);
    }

    /**
     * Accumulates vertex normals across faces of render type 0 (smooth shading).
     */
    public static List<ModelNormal> calculateVertexNormals(
            List<TransformedVertex> vertices,
            int[] indices,
            int triangleCount,
            int[] renderTypes,
            boolean mirror
    ) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");

        List<ModelNormal> normals = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            normals.add(ModelNormal.ZERO);
        }

        for (int face = 0; face < triangleCount; face++) {
            int offset = face * 3;
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];

            if (mirror) {
                int swap = b;
                b = c;
                c = swap;
            }

            TransformedVertex v0 = vertices.get(a);
            TransformedVertex v1 = vertices.get(b);
            TransformedVertex v2 = vertices.get(c);
            ModelNormal fNormal = faceNormal(v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z());

            int renderType = (renderTypes != null && face < renderTypes.length) ? renderTypes[face] : 0;
            // OSRS accumulates only render type 0 faces into vertex normals.
            if (renderType == 0) {
                normals.set(a, normals.get(a).add(fNormal));
                normals.set(b, normals.get(b).add(fNormal));
                normals.set(c, normals.get(c).add(fNormal));
            }
        }

        return List.copyOf(normals);
    }
}
