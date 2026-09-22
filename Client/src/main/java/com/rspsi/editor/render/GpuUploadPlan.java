package com.rspsi.editor.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, API-neutral CPU upload plan. Native renderers turn this into
 * vertex/index buffers, texture bindings, and draw calls; they do not decode
 * cache definitions or rebuild scene ordering.
 *
 * <p>The ordinary constructor retains the historic eager flat representation.
 * Incremental production compilation may instead install lazy flat list views:
 * counts and indexed command access remain cheap, while the giant scene-wide
 * lists are materialized only if a compatibility caller reads them directly.</p>
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
        return directVertexAt(directIndexAt(command.firstIndex() + indexOffset));
    }

    /**
     * Stable scene-wide vertex index used while partitioning a lazy flat plan
     * into zone-local buffers without forcing the flat compatibility lists.
     */
    int indexedVertexIndex(int commandIndex, int indexOffset) {
        GpuDrawCommand command = commands.get(commandIndex);
        if (indexOffset < 0 || indexOffset >= command.indexCount()) {
            throw new IndexOutOfBoundsException("indexOffset " + indexOffset);
        }
        return directIndexAt(command.firstIndex() + indexOffset);
    }

    GpuSceneVertex directVertexAt(int vertexIndex) {
        LazyGpuFlatGeometry lazy = LazyGpuFlatGeometry.ownerOf(vertices);
        return lazy == null ? vertices.get(vertexIndex) : lazy.directVertex(vertexIndex);
    }

    int directIndexAt(int indexOffset) {
        LazyGpuFlatGeometry lazy = LazyGpuFlatGeometry.ownerOf(indices);
        return lazy == null ? indices.get(indexOffset) : lazy.directIndex(indexOffset);
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

    /** Number of scene-wide compatibility materializations performed by this plan. */
    public int flatMaterializationCount() {
        LazyGpuFlatGeometry lazy = LazyGpuFlatGeometry.ownerOf(vertices);
        return lazy == null ? (vertexCount() == 0 && indexCount() == 0 ? 0 : 1)
                : lazy.materializationCount();
    }

    /** True once the scene-wide flat compatibility lists exist in memory. */
    public boolean flatMaterialized() {
        LazyGpuFlatGeometry lazy = LazyGpuFlatGeometry.ownerOf(vertices);
        return lazy == null || lazy.isMaterialized();
    }

    static GpuUploadPlan lazy(LazyGpuFlatGeometry geometry,
                              List<GpuDrawCommand> commands,
                              List<GpuTextureTriangle> textureTriangles,
                              Map<Integer, RenderTextureResource> textures,
                              List<SceneOccluder> occluders,
                              String fingerprint) {
        Objects.requireNonNull(geometry, "geometry");
        return new GpuUploadPlan(geometry.verticesView(), geometry.indicesView(), commands,
                textureTriangles, textures, occluders, fingerprint);
    }

    /** Compatibility constructor for callers that do not carry occluder inputs. */
    public GpuUploadPlan(List<GpuSceneVertex> vertices, List<Integer> indices,
                         List<GpuDrawCommand> commands, List<GpuTextureTriangle> textureTriangles,
                         Map<Integer, RenderTextureResource> textures, String fingerprint) {
        this(vertices, indices, commands, textureTriangles, textures, List.of(), fingerprint);
    }

    public GpuUploadPlan {
        vertices = Objects.requireNonNull(vertices, "vertices");
        indices = Objects.requireNonNull(indices, "indices");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        textureTriangles = List.copyOf(Objects.requireNonNull(textureTriangles, "textureTriangles"));
        textures = Map.copyOf(Objects.requireNonNull(textures, "textures"));
        occluders = List.copyOf(Objects.requireNonNull(occluders, "occluders"));
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").trim();
        if (fingerprint.isEmpty()) throw new IllegalArgumentException("GPU upload fingerprint cannot be empty");

        LazyGpuFlatGeometry vertexOwner = LazyGpuFlatGeometry.ownerOf(vertices);
        LazyGpuFlatGeometry indexOwner = LazyGpuFlatGeometry.ownerOf(indices);
        if (vertexOwner != indexOwner) {
            throw new IllegalArgumentException("GPU upload flat geometry views must share one owner");
        }
        if (vertexOwner == null) {
            vertices = List.copyOf(vertices);
            indices = List.copyOf(indices);
            int vertexCount = vertices.size();
            if (indices.stream().anyMatch(index -> index == null || index < 0 || index >= vertexCount)) {
                throw new IllegalArgumentException("GPU upload index references a missing vertex");
            }
        } else if (vertices.size() != vertexOwner.vertexCount()
                || indices.size() != vertexOwner.indexCount()) {
            throw new IllegalArgumentException("Lazy GPU upload geometry count mismatch");
        }

        for (GpuDrawCommand command : commands) {
            if (command.firstIndex() + command.indexCount() > indices.size()) {
                throw new IllegalArgumentException("GPU draw command exceeds the index buffer");
            }
        }
    }
}
