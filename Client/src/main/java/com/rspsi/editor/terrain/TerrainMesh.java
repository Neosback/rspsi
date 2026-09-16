package com.rspsi.editor.terrain;

import java.util.List;
import java.util.Objects;

/** Immutable renderer-independent terrain geometry. */
public record TerrainMesh(List<TerrainVertex> vertices, List<TerrainFace> faces) {
    public TerrainMesh {
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
        for (TerrainFace face : faces) {
            if (face.a() >= vertices.size() || face.b() >= vertices.size() || face.c() >= vertices.size()) {
                throw new IllegalArgumentException("Terrain face references a missing vertex");
            }
        }
    }
}
