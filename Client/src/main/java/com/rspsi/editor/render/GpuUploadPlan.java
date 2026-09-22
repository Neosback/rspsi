package com.rspsi.editor.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, API-neutral CPU upload plan. Native renderers turn this into
 * vertex/index buffers, texture bindings, and draw calls; they do not decode
 * cache definitions or rebuild scene ordering.
 */
public record GpuUploadPlan(
        List<GpuSceneVertex> vertices,
        List<Integer> indices,
        List<GpuDrawCommand> commands,
        List<GpuTextureTriangle> textureTriangles,
        Map<Integer, RenderTextureResource> textures,
        List<SceneOccluder> occluders,
        String fingerprint
) implements GpuCommandGeometry {
    @Override
    public int commandCount() {
        return commands.size();
    }

    @Override
    public GpuDrawCommand command(int commandIndex) {
        return commands.get(commandIndex);
    }

    @Override
    public GpuSceneVertex indexedVertex(int commandIndex, int indexOffset) {
        GpuDrawCommand command = commands.get(commandIndex);
        if (indexOffset < 0 || indexOffset >= command.indexCount()) {
            throw new IndexOutOfBoundsException("indexOffset " + indexOffset);
        }
        return vertices.get(indices.get(command.firstIndex() + indexOffset));
    }

    @Override
    public int vertexCount() {
        return vertices.size();
    }

    @Override
    public int indexCount() {
        return indices.size();
    }

    @Override
    public void forEachUniqueVertex(java.util.function.Consumer<GpuSceneVertex> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        vertices.forEach(consumer);
    }

    /** Compatibility constructor for callers that do not carry occluder inputs. */
    public GpuUploadPlan(List<GpuSceneVertex> vertices, List<Integer> indices,
                         List<GpuDrawCommand> commands, List<GpuTextureTriangle> textureTriangles,
                         Map<Integer, RenderTextureResource> textures, String fingerprint) {
        this(vertices, indices, commands, textureTriangles, textures, List.of(), fingerprint);
    }

    public GpuUploadPlan {
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        indices = List.copyOf(Objects.requireNonNull(indices, "indices"));
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        textureTriangles = List.copyOf(Objects.requireNonNull(textureTriangles, "textureTriangles"));
        textures = Map.copyOf(Objects.requireNonNull(textures, "textures"));
        occluders = List.copyOf(Objects.requireNonNull(occluders, "occluders"));
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").trim();
        if (fingerprint.isEmpty()) throw new IllegalArgumentException("GPU upload fingerprint cannot be empty");
        int vertexCount = vertices.size();
        if (indices.stream().anyMatch(index -> index == null || index < 0 || index >= vertexCount)) {
            throw new IllegalArgumentException("GPU upload index references a missing vertex");
        }
        for (GpuDrawCommand command : commands) {
            if (command.firstIndex() + command.indexCount() > indices.size()) {
                throw new IllegalArgumentException("GPU draw command exceeds the index buffer");
            }
        }
    }
}
