package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/** Complete renderer-neutral model geometry and material packet. */
public record ModelRenderPacket(
        TileCoordinate anchor,
        int objectId,
        ObjectCategory category,
        List<ModelVertex> vertices,
        List<ModelTriangle> triangles,
        List<TextureTriangle> textureTriangles,
        int animationId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean supportsAnimation,
        boolean supportsParticles,
        int placementHeight
) {
    /** Compatibility constructor before model placement elevation was explicit. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles, 0);
    }

    public ModelRenderPacket {
        anchor = Objects.requireNonNull(anchor, "anchor");
        category = Objects.requireNonNull(category, "category");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        triangles = List.copyOf(Objects.requireNonNull(triangles, "triangles"));
        textureTriangles = List.copyOf(Objects.requireNonNull(textureTriangles, "textureTriangles"));
        if (objectId < 0 || animationId < -1 || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid model packet identity or bounds");
        }
        for (ModelTriangle triangle : triangles) {
            if (triangle.a() >= vertices.size() || triangle.b() >= vertices.size()
                    || triangle.c() >= vertices.size()) {
                throw new IllegalArgumentException("Model triangle references a missing vertex");
            }
        }
        for (TextureTriangle triangle : textureTriangles) {
            if (triangle.a() >= vertices.size() || triangle.b() >= vertices.size()
                    || triangle.c() >= vertices.size()) {
                throw new IllegalArgumentException("Texture triangle references a missing vertex");
            }
        }
    }

    /** Returns the same derived geometry projected onto a world tile anchor. */
    public ModelRenderPacket withAnchor(TileCoordinate newAnchor) {
        return new ModelRenderPacket(newAnchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight);
    }

    /** Triangle indices suitable for the opaque submission pass. */
    public List<Integer> opaqueTriangleIndices() {
        return triangleIndices(false);
    }

    /** Triangle indices that require alpha/visibility-aware submission. */
    public List<Integer> transparentTriangleIndices() {
        return triangleIndices(true);
    }

    private List<Integer> triangleIndices(boolean transparent) {
        java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
        for (int index = 0; index < triangles.size(); index++) {
            ModelTriangle face = triangles.get(index);
            // Client render type 2 is an intentionally hidden face, not an
            // opaque submission.
            if (face.renderType() == 2 || face.alpha() == 255) continue;
            boolean faceTransparent = face.alpha() != 0 || face.renderType() == 3;
            if (faceTransparent == transparent) indices.add(index);
        }
        return List.copyOf(indices);
    }
}
