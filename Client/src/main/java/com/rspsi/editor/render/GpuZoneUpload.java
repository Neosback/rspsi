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
        indices = ImmutableIntList.copyOf(Objects.requireNonNull(indices, "indices"));
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        int vertexCount = vertices.size();
        for (int offset = 0; offset < indices.size(); offset++) {
            int index = primitiveIndexAt(indices, offset);
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException("Zone index references a missing vertex");
            }
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

    /** Primitive index access for native/picking hot paths without Integer boxing. */
    public int indexAt(int offset) {
        return primitiveIndexAt(indices, offset);
    }

    /** Compatibility aggregate for callers that only need current native residency identity. */
    public long fingerprint() {
        return fingerprints.nativeFingerprint();
    }

    public static GpuZoneStreamFingerprints fingerprints(
            List<GpuSceneVertex> vertices, List<Integer> indices) {
        return fingerprints(vertices, indices, List.of());
    }

    /**
     * Computes stream identities including command-owned material state that is
     * packed into the native face stream.
     */
    public static GpuZoneStreamFingerprints fingerprints(
            List<GpuSceneVertex> vertices, List<Integer> indices,
            List<GpuDrawCommand> commands) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(commands, "commands");

        long geometry = mix(1125899906842597L, vertices.size());
        long vertexShading = mix(1125899906842597L, vertices.size());
        if (indices.size() % 3 != 0) {
            throw new IllegalArgumentException("Zone topology must contain complete triangles");
        }
        long faceShading = mix(1125899906842597L, indices.size() / 3);
        long normals = mix(1125899906842597L, vertices.size());
        long pickerIds = mix(1125899906842597L, vertices.size());
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

            pickerIds = mix(pickerIds, PickerId.encode(
                    vertex.pickerPlane(), vertex.pickerTileX(),
                    vertex.pickerTileY(), vertex.pickerSlot()));
        }

        for (int offset = 0; offset < indices.size(); offset += 3) {
            GpuSceneVertex first = vertices.get(primitiveIndexAt(indices, offset));
            GpuSceneVertex second = vertices.get(primitiveIndexAt(indices, offset + 1));
            GpuSceneVertex third = vertices.get(primitiveIndexAt(indices, offset + 2));
            if (!sameFaceShading(first, second) || !sameFaceShading(first, third)) {
                throw new IllegalArgumentException(
                        "Face shading metadata must be constant across one triangle");
            }
            if (!samePickerId(first, second) || !samePickerId(first, third)) {
                throw new IllegalArgumentException(
                        "Picker metadata must be constant across one triangle");
            }
            faceShading = mix(faceShading, first.alpha());
            faceShading = mix(faceShading, first.renderType());
            faceShading = mix(faceShading, first.priority());
            faceShading = mix(faceShading, first.textureId());
        }

        for (GpuDrawCommand command : commands) {
            faceShading = mix(faceShading, command.textureId());
            faceShading = mix(faceShading, command.depthBias());
            faceShading = mix(faceShading,
                    command.layer() == SceneLayer.Kind.TERRAIN ? 1 : 0);
        }

        long topology = mix(1125899906842597L, indices.size());
        for (int offset = 0; offset < indices.size(); offset++) {
            topology = mix(topology, primitiveIndexAt(indices, offset));
        }
        return new GpuZoneStreamFingerprints(
                geometry, vertexShading, faceShading, topology, normals, pickerIds);
    }

    /** Current native-residency aggregate retained for flat-plan callers. */
    public static long fingerprint(List<GpuSceneVertex> vertices, List<Integer> indices) {
        return fingerprints(vertices, indices).nativeFingerprint();
    }

    private static int primitiveIndexAt(List<Integer> values, int offset) {
        if (values instanceof ImmutableIntList primitive) {
            return primitive.getInt(offset);
        }
        Integer value = values.get(offset);
        if (value == null) throw new NullPointerException("Renderer index cannot be null");
        return value;
    }

    private static boolean sameFaceShading(GpuSceneVertex first, GpuSceneVertex second) {
        return first.alpha() == second.alpha()
                && first.renderType() == second.renderType()
                && first.priority() == second.priority();
    }

    private static boolean samePickerId(GpuSceneVertex first, GpuSceneVertex second) {
        return first.pickerPlane() == second.pickerPlane()
                && first.pickerTileX() == second.pickerTileX()
                && first.pickerTileY() == second.pickerTileY()
                && first.pickerSlot() == second.pickerSlot();
    }

    private static long mix(long hash, int value) {
        return hash * 31L + value;
    }
}
