package com.rspsi.editor.render;

import java.util.List;
import java.util.Objects;

/** Immutable local vertex/index allocation for one absolute 8x8 world zone. */
public record GpuZoneUpload(
        WorldZoneCoordinate zone,
        List<GpuSceneVertex> vertices,
        List<Integer> indices,
        List<GpuDrawCommand> commands,
        GpuZoneStreamFingerprints fingerprints
) {
    public GpuZoneUpload {
        zone = Objects.requireNonNull(zone, "zone");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        indices = List.copyOf(Objects.requireNonNull(indices, "indices"));
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        int vertexCount = vertices.size();
        if (indices.stream().anyMatch(index -> index == null || index < 0 || index >= vertexCount)) {
            throw new IllegalArgumentException("Zone index references a missing vertex");
        }
        for (GpuDrawCommand command : commands) {
            if (!zone.contains(command.tile())) {
                throw new IllegalArgumentException("Zone command belongs to another world zone");
            }
            if (command.firstIndex() + command.indexCount() > indices.size()) {
                throw new IllegalArgumentException("Zone draw command exceeds the local index buffer");
            }
        }
    }

    /** Compatibility aggregate for callers that only need current native residency identity. */
    public long fingerprint() {
        return fingerprints.nativeFingerprint();
    }

    public static GpuZoneStreamFingerprints fingerprints(
            List<GpuSceneVertex> vertices, List<Integer> indices) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");

        long geometry = mix(1125899906842597L, vertices.size());
        long vertexShading = mix(1125899906842597L, vertices.size());
        if (indices.size() % 3 != 0) {
            throw new IllegalArgumentException("Zone topology must contain complete triangles");
        }
        long faceShading = mix(1125899906842597L, indices.size() / 3);
        long normals = mix(1125899906842597L, vertices.size());
        for (GpuSceneVertex vertex : vertices) {
            geometry = mix(geometry, Float.floatToIntBits(vertex.x()));
            geometry = mix(geometry, Float.floatToIntBits(vertex.y()));
            geometry = mix(geometry, Float.floatToIntBits(vertex.z()));
            geometry = mix(geometry, Float.floatToIntBits(vertex.u()));
            geometry = mix(geometry, Float.floatToIntBits(vertex.v()));

            vertexShading = mix(vertexShading, vertex.encodedColor());
            vertexShading = mix(vertexShading, vertex.colorEncoding().ordinal());

            normals = mix(normals, vertex.normalX());
            normals = mix(normals, vertex.normalY());
            normals = mix(normals, vertex.normalZ());
            normals = mix(normals, vertex.normalMagnitude());
        }

        for (int offset = 0; offset < indices.size(); offset += 3) {
            GpuFaceShading face = vertices.get(indices.get(offset)).faceShading();
            GpuFaceShading second = vertices.get(indices.get(offset + 1)).faceShading();
            GpuFaceShading third = vertices.get(indices.get(offset + 2)).faceShading();
            if (!face.equals(second) || !face.equals(third)) {
                throw new IllegalArgumentException(
                        "Face shading metadata must be constant across one triangle");
            }
            faceShading = mix(faceShading, face.alpha());
            faceShading = mix(faceShading, face.renderType());
            faceShading = mix(faceShading, face.priority());
        }

        long topology = mix(1125899906842597L, indices.size());
        for (int index : indices) topology = mix(topology, index);
        return new GpuZoneStreamFingerprints(
                geometry, vertexShading, faceShading, topology, normals);
    }

    /** Current native-residency aggregate retained for flat-plan callers. */
    public static long fingerprint(List<GpuSceneVertex> vertices, List<Integer> indices) {
        return fingerprints(vertices, indices).nativeFingerprint();
    }

    private static long mix(long hash, int value) {
        return hash * 31L + value;
    }
}
