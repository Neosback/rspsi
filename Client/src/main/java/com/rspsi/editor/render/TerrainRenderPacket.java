package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/**
 * Complete terrain presentation for one tile. This is the contract consumed
 * by a software renderer or GPU uploader; it is not authored cache state.
 */
public record TerrainRenderPacket(
        TileCoordinate coordinate,
        List<TerrainRenderVertex> vertices,
        List<TerrainRenderFace> faces,
        int shape,
        int rotation,
        int textureId,
        int underlayHsl,
        int overlayHsl,
        boolean flat,
        boolean overlayHidden
) {
    public TerrainRenderPacket {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
        if (shape < 0 || rotation < 0 || rotation > 3 || textureId < -1
                || underlayHsl < 0 || overlayHsl < -1) {
            throw new IllegalArgumentException("Invalid terrain render metadata");
        }
        for (TerrainRenderFace face : faces) {
            if (face.a() >= vertices.size() || face.b() >= vertices.size() || face.c() >= vertices.size()) {
                throw new IllegalArgumentException("Terrain face references a missing vertex");
            }
        }
    }
}
