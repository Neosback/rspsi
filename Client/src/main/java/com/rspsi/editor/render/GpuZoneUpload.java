package com.rspsi.editor.render;

import java.util.List;
import java.util.Objects;

/** Immutable local vertex/index allocation for one absolute 8x8 world zone. */
public record GpuZoneUpload(
        WorldZoneCoordinate zone,
        List<GpuSceneVertex> vertices,
        List<Integer> indices,
        List<GpuDrawCommand> commands,
        long fingerprint
) {
    public GpuZoneUpload {
        zone = Objects.requireNonNull(zone, "zone");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        indices = List.copyOf(Objects.requireNonNull(indices, "indices"));
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
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

    public static long fingerprint(List<GpuSceneVertex> vertices, List<Integer> indices) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");
        long hash = 1125899906842597L;
        for (GpuSceneVertex v : vertices) {
            hash = hash * 31L + Float.floatToIntBits(v.x());
            hash = hash * 31L + Float.floatToIntBits(v.y());
            hash = hash * 31L + Float.floatToIntBits(v.z());
            hash = hash * 31L + v.encodedColor();
            hash = hash * 31L + v.alpha();
            hash = hash * 31L + v.renderType();
            hash = hash * 31L + v.textureId();
            hash = hash * 31L + v.priority();
        }
        for (int index : indices) hash = hash * 31L + index;
        return hash;
    }
}
