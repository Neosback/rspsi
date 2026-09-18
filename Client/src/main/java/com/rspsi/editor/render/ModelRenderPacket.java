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
        boolean supportsParticles
) {
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
}
