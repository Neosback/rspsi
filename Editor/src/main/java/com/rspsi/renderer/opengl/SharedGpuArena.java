package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuZoneUpload;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.PickerId;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glBufferSubData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glVertexAttribIPointer;

/**
 * One shared native geometry arena for all resident 8x8 map zones.
 *
 * <p>Logical zones remain the editor invalidation unit, but they occupy slices
 * of these shared buffers instead of creating one VAO and up to six VBOs per
 * zone. This is the OpenGL 3.3-compatible foundation for cross-zone
 * multi-draw today and MDI/persistent mapping on capable drivers later.</p>
 */
final class SharedGpuArena implements AutoCloseable {
    private final GpuUploadScratch scratch = new GpuUploadScratch();

    private int vao;
    private int geometryVbo;
    private int vertexShadingVbo;
    private int faceMetadataVbo;
    private int normalVbo;
    private int pickerVbo;
    private int ibo;
    private int vertexCapacity;
    private int indexCapacity;
    private boolean normalsEnabled;
    private boolean pickersEnabled;

    void allocate(int vertices, int indices, boolean normals, boolean pickers) {
        if (vertices < 1 || indices < 1) {
            throw new IllegalArgumentException("Shared GPU arena capacity must be positive");
        }
        closeNative();
        normalsEnabled = normals;
        pickersEnabled = pickers;
        vertexCapacity = vertices;
        indexCapacity = indices;

        vao = glGenVertexArrays();
        geometryVbo = glGenBuffers();
        vertexShadingVbo = glGenBuffers();
        faceMetadataVbo = glGenBuffers();
        normalVbo = normals ? glGenBuffers() : 0;
        pickerVbo = pickers ? glGenBuffers() : 0;
        ibo = glGenBuffers();

        allocateArrayBuffer(geometryVbo,
                (long) vertices * NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX * Float.BYTES);
        allocateArrayBuffer(vertexShadingVbo,
                (long) vertices * NativeSceneVertexLayout.VERTEX_SHADING_FLOATS_PER_VERTEX * Float.BYTES);
        allocateArrayBuffer(faceMetadataVbo,
                (long) vertices * NativeSceneVertexLayout.FACE_METADATA_FLOATS_PER_VERTEX * Float.BYTES);
        if (normalVbo != 0) {
            allocateArrayBuffer(normalVbo,
                    (long) vertices * NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX * Float.BYTES);
        }
        if (pickerVbo != 0) {
            allocateArrayBuffer(pickerVbo, (long) vertices * Integer.BYTES);
        }

        glBindVertexArray(vao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) indices * Integer.BYTES, GL_DYNAMIC_DRAW);
        glBindVertexArray(0);
        setupVao();
    }

    private static void allocateArrayBuffer(int buffer, long bytes) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, bytes, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void setupVao() {
        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo);
        int geometryStride = NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, geometryStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, geometryStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, vertexShadingVbo);
        int shadingStride =
                NativeSceneVertexLayout.VERTEX_SHADING_FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(2, 1, GL_FLOAT, false, shadingStride, 0L);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(5, 3, GL_FLOAT, false, shadingStride, Float.BYTES);
        glEnableVertexAttribArray(5);

        glBindBuffer(GL_ARRAY_BUFFER, faceMetadataVbo);
        int faceStride = NativeSceneVertexLayout.FACE_METADATA_FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(3, 1, GL_FLOAT, false, faceStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, faceStride, Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, faceStride, 2L * Float.BYTES);
        glEnableVertexAttribArray(6);

        if (normalVbo != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, normalVbo);
            int normalStride = NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX * Float.BYTES;
            glVertexAttribPointer(7, 4, GL_FLOAT, false, normalStride, 0L);
            glEnableVertexAttribArray(7);
        }

        if (pickerVbo != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, pickerVbo);
            glVertexAttribIPointer(8, 1, GL_UNSIGNED_INT, Integer.BYTES, 0L);
            glEnableVertexAttribArray(8);
        }

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadGeometry(int vertexOffset, List<GpuSceneVertex> vertices) {
        FloatBuffer data = scratch.vertices(
                vertices.size() * NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            data.put(vertex.x()).put(vertex.y()).put(vertex.z())
                    .put(vertex.u()).put(vertex.v());
        }
        data.flip();
        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo);
        glBufferSubData(GL_ARRAY_BUFFER,
                (long) vertexOffset * NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX * Float.BYTES,
                data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadVertexShading(int vertexOffset, List<GpuSceneVertex> vertices) {
        FloatBuffer data = scratch.vertices(
                vertices.size() * NativeSceneVertexLayout.VERTEX_SHADING_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            int rgb = vertex.colorEncoding() == GpuColorEncoding.PACKED_JAGEX_HSL
                    ? OsrsTerrainColorMath.packedHslToRgb(vertex.encodedColor(), 0.6)
                    : 0;
            data.put(vertex.encodedColor())
                    .put(((rgb >>> 16) & 0xFF) / 255.0f)
                    .put(((rgb >>> 8) & 0xFF) / 255.0f)
                    .put((rgb & 0xFF) / 255.0f);
        }
        data.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vertexShadingVbo);
        glBufferSubData(GL_ARRAY_BUFFER,
                (long) vertexOffset * NativeSceneVertexLayout.VERTEX_SHADING_FLOATS_PER_VERTEX * Float.BYTES,
                data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadFaceMetadata(int vertexOffset, List<GpuSceneVertex> vertices) {
        FloatBuffer data = scratch.vertices(
                vertices.size() * NativeSceneVertexLayout.FACE_METADATA_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            data.put((float) vertex.alpha())
                    .put((float) vertex.renderType())
                    .put((float) vertex.priority());
        }
        data.flip();
        glBindBuffer(GL_ARRAY_BUFFER, faceMetadataVbo);
        glBufferSubData(GL_ARRAY_BUFFER,
                (long) vertexOffset * NativeSceneVertexLayout.FACE_METADATA_FLOATS_PER_VERTEX * Float.BYTES,
                data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadNormals(int vertexOffset, List<GpuSceneVertex> vertices) {
        if (normalVbo == 0) {
            throw new IllegalStateException("Normal stream upload requested without a shared normal buffer");
        }
        FloatBuffer data = scratch.vertices(
                vertices.size() * NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            data.put((float) vertex.normalX())
                    .put((float) vertex.normalY())
                    .put((float) vertex.normalZ())
                    .put((float) vertex.normalMagnitude());
        }
        data.flip();
        glBindBuffer(GL_ARRAY_BUFFER, normalVbo);
        glBufferSubData(GL_ARRAY_BUFFER,
                (long) vertexOffset * NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX * Float.BYTES,
                data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadPickerIds(int vertexOffset, List<GpuSceneVertex> vertices) {
        if (pickerVbo == 0) {
            throw new IllegalStateException("Picker stream upload requested without a shared picker buffer");
        }
        IntBuffer data = scratch.indices(vertices.size());
        for (GpuSceneVertex vertex : vertices) {
            data.put(PickerId.encode(
                    vertex.pickerPlane(), vertex.pickerTileX(),
                    vertex.pickerTileY(), vertex.pickerSlot()));
        }
        data.flip();
        glBindBuffer(GL_ARRAY_BUFFER, pickerVbo);
        glBufferSubData(GL_ARRAY_BUFFER, (long) vertexOffset * Integer.BYTES, data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void uploadIndices(int indexOffset, int vertexBase, GpuZoneUpload zone) {
        IntBuffer data = scratch.indices(zone.indices().size());
        for (int offset = 0; offset < zone.indices().size(); offset++) {
            data.put(vertexBase + zone.indexAt(offset));
        }
        data.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, (long) indexOffset * Integer.BYTES, data);
        glBindVertexArray(0);
    }

    boolean allocated() {
        return vao != 0;
    }

    int vao() { return vao; }
    int geometryVbo() { return geometryVbo; }
    int vertexShadingVbo() { return vertexShadingVbo; }
    int faceMetadataVbo() { return faceMetadataVbo; }
    int normalVbo() { return normalVbo; }
    int pickerVbo() { return pickerVbo; }
    int ibo() { return ibo; }
    int vertexCapacity() { return vertexCapacity; }
    int indexCapacity() { return indexCapacity; }
    boolean normalsEnabled() { return normalsEnabled; }
    boolean pickersEnabled() { return pickersEnabled; }

    int stagingVertexCapacityFloats() { return scratch.vertexCapacityFloats(); }
    int stagingIndexCapacity() { return scratch.indexCapacity(); }
    int stagingVertexGrowths() { return scratch.vertexGrowths(); }
    int stagingIndexGrowths() { return scratch.indexGrowths(); }

    void reset() {
        closeNative();
    }

    private void closeNative() {
        if (vao != 0) glDeleteVertexArrays(vao);
        if (geometryVbo != 0) glDeleteBuffers(geometryVbo);
        if (vertexShadingVbo != 0) glDeleteBuffers(vertexShadingVbo);
        if (faceMetadataVbo != 0) glDeleteBuffers(faceMetadataVbo);
        if (normalVbo != 0) glDeleteBuffers(normalVbo);
        if (pickerVbo != 0) glDeleteBuffers(pickerVbo);
        if (ibo != 0) glDeleteBuffers(ibo);
        vao = 0;
        geometryVbo = 0;
        vertexShadingVbo = 0;
        faceMetadataVbo = 0;
        normalVbo = 0;
        pickerVbo = 0;
        ibo = 0;
        vertexCapacity = 0;
        indexCapacity = 0;
    }

    @Override
    public void close() {
        closeNative();
        scratch.close();
    }
}
