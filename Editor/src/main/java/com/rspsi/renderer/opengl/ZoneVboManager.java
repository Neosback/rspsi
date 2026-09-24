package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZoneStreamFingerprints;
import com.rspsi.editor.render.GpuZoneUpload;
import com.rspsi.editor.render.GpuZonedDrawCommand;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.WorldZoneCoordinate;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

/**
 * Manages 8x8 zone-partitioned GPU vertex and index buffers.
 *
 * <p>Stable position/UV geometry and vanilla shading data live in independent
 * VBOs. A shading-only change can therefore update the small shading stream
 * without re-uploading positions, UVs or topology. Renderer-neutral auxiliary
 * data such as normals remains outside these vanilla streams until a shader
 * actually consumes it.</p>
 */
public final class ZoneVboManager implements AutoCloseable {
    public static final int ZONE_SIZE = 8;

    public record ZoneAllocation(
            long zoneKey,
            int vao,
            int geometryVbo,
            int shadingVbo,
            int normalVbo,
            int ibo,
            long geometryFingerprint,
            long shadingFingerprint,
            long normalFingerprint,
            long indexFingerprint
    ) {
        ZoneAllocation(long zoneKey, int vao, int geometryVbo, int shadingVbo, int ibo,
                       long geometryFingerprint, long shadingFingerprint, long indexFingerprint) {
            this(zoneKey, vao, geometryVbo, shadingVbo, 0, ibo,
                    geometryFingerprint, shadingFingerprint, 0L, indexFingerprint);
        }
    }

    record StreamUploadDecision(boolean geometry, boolean shading,
                                boolean normals, boolean indices) {
        boolean any() {
            return geometry || shading || normals || indices;
        }
    }

    static StreamUploadDecision streamUploadDecision(
            ZoneAllocation existing, GpuZoneStreamFingerprints fingerprints) {
        return streamUploadDecision(existing, fingerprints, false);
    }

    static StreamUploadDecision streamUploadDecision(
            ZoneAllocation existing, GpuZoneStreamFingerprints fingerprints,
            boolean normalStreamEnabled) {
        if (fingerprints == null) {
            throw new IllegalArgumentException("Zone stream fingerprints cannot be null");
        }
        return new StreamUploadDecision(
                existing == null || existing.geometryFingerprint() != fingerprints.geometry(),
                existing == null || existing.shadingFingerprint() != fingerprints.shading(),
                normalStreamEnabled && (existing == null
                        || existing.normalFingerprint() != fingerprints.normals()),
                existing == null || existing.indexFingerprint() != fingerprints.indices());
    }

    private final Map<Long, ZoneAllocation> allocations = new HashMap<>();
    private final GpuUploadScratch uploadScratch = new GpuUploadScratch();
    private final boolean normalStreamEnabled;
    private int[] commandLocalFirstIndices = new int[0];
    private long[] commandZoneKeys = new long[0];
    private int dirtyZonesUploadedCount;
    private int reusedAllocationsCount;
    private int totalZonesCount;
    private int geometryStreamUploads;
    private int shadingStreamUploads;
    private int indexStreamUploads;
    private int normalStreamUploads;

    public ZoneVboManager() {
        this(false);
    }

    ZoneVboManager(boolean normalStreamEnabled) {
        this.normalStreamEnabled = normalStreamEnabled;
    }

    public static long zoneKey(WorldTileAddress tile) {
        return WorldZoneCoordinate.from(tile).key();
    }

    public void upload(GpuUploadPlan plan) {
        resetUploadMetrics();
        if (plan == null || plan.commands().isEmpty() || plan.vertices().isEmpty()) {
            close();
            totalZonesCount = 0;
            return;
        }

        List<GpuDrawCommand> commands = plan.commands();
        if (commandLocalFirstIndices.length < commands.size()) {
            commandLocalFirstIndices = new int[commands.size()];
            commandZoneKeys = new long[commands.size()];
        }

        Map<Long, List<Integer>> zoneToCommandIndices = new HashMap<>();
        for (int i = 0; i < commands.size(); i++) {
            GpuDrawCommand command = commands.get(i);
            long key = zoneKey(command.tile());
            commandZoneKeys[i] = key;
            zoneToCommandIndices.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
        }

        totalZonesCount = zoneToCommandIndices.size();
        Set<Long> activeZones = new HashSet<>(zoneToCommandIndices.keySet());

        int[] globalToLocal = new int[plan.vertices().size()];
        Arrays.fill(globalToLocal, -1);
        List<Integer> touchedGlobal = new ArrayList<>();
        List<GpuSceneVertex> localVertices = new ArrayList<>();
        List<Integer> localIndices = new ArrayList<>();

        for (Map.Entry<Long, List<Integer>> entry : zoneToCommandIndices.entrySet()) {
            long key = entry.getKey();
            List<Integer> cmdIndices = entry.getValue();

            localVertices.clear();
            localIndices.clear();

            for (int cmdIdx : cmdIndices) {
                GpuDrawCommand cmd = commands.get(cmdIdx);
                commandLocalFirstIndices[cmdIdx] = localIndices.size();
                for (int i = 0; i < cmd.indexCount(); i++) {
                    int globalIndex = plan.indices().get(cmd.firstIndex() + i);
                    int localIndex = globalToLocal[globalIndex];
                    if (localIndex == -1) {
                        localIndex = localVertices.size();
                        localVertices.add(plan.vertices().get(globalIndex));
                        globalToLocal[globalIndex] = localIndex;
                        touchedGlobal.add(globalIndex);
                    }
                    localIndices.add(localIndex);
                }
            }

            for (int globalIndex : touchedGlobal) {
                globalToLocal[globalIndex] = -1;
            }
            touchedGlobal.clear();

            GpuZoneStreamFingerprints fingerprints =
                    GpuZoneUpload.fingerprints(localVertices, localIndices);
            uploadZone(key, localVertices, localIndices, fingerprints);
        }

        removeInactive(activeZones);
    }

    /**
     * Uploads a pre-partitioned native plan without scanning or copying the
     * global flat vertex/index arrays on the render thread.
     */
    public void upload(GpuZonedUploadPlan plan) {
        resetUploadMetrics();
        if (plan == null || plan.zones().isEmpty() || plan.commandRefs().isEmpty()) {
            close();
            totalZonesCount = 0;
            return;
        }

        List<GpuZonedDrawCommand> refs = plan.commandRefs();
        if (commandLocalFirstIndices.length < refs.size()) {
            commandLocalFirstIndices = new int[refs.size()];
            commandZoneKeys = new long[refs.size()];
        }
        for (int index = 0; index < refs.size(); index++) {
            GpuZonedDrawCommand ref = refs.get(index);
            commandLocalFirstIndices[index] = ref.localFirstIndex();
            commandZoneKeys[index] = ref.zone().key();
        }

        totalZonesCount = plan.zones().size();
        Set<Long> activeZones = new HashSet<>();
        for (GpuZoneUpload zone : plan.zones().values()) {
            long key = zone.zone().key();
            activeZones.add(key);
            uploadZone(key, zone.vertices(), zone.indices(), zone.fingerprints());
        }

        removeInactive(activeZones);
    }

    private void uploadZone(long key,
                            List<GpuSceneVertex> vertices,
                            List<Integer> indices,
                            GpuZoneStreamFingerprints fingerprints) {
        ZoneAllocation existing = allocations.get(key);
        StreamUploadDecision decision =
                streamUploadDecision(existing, fingerprints, normalStreamEnabled);
        if (!decision.any()) {
            reusedAllocationsCount++;
            return;
        }

        int vao;
        int geometryVbo;
        int shadingVbo;
        int normalVbo;
        int ibo;
        if (existing == null) {
            vao = glGenVertexArrays();
            geometryVbo = glGenBuffers();
            shadingVbo = glGenBuffers();
            normalVbo = normalStreamEnabled ? glGenBuffers() : 0;
            ibo = glGenBuffers();
            setupVao(vao, geometryVbo, shadingVbo, normalVbo, ibo);
        } else {
            vao = existing.vao();
            geometryVbo = existing.geometryVbo();
            shadingVbo = existing.shadingVbo();
            normalVbo = existing.normalVbo();
            ibo = existing.ibo();
        }

        if (decision.geometry()) {
            uploadGeometry(geometryVbo, vertices);
            geometryStreamUploads++;
        }
        if (decision.shading()) {
            uploadShading(shadingVbo, vertices);
            shadingStreamUploads++;
        }
        if (decision.normals()) {
            uploadNormals(normalVbo, vertices);
            normalStreamUploads++;
        }
        if (decision.indices()) {
            uploadIndices(vao, ibo, indices);
            indexStreamUploads++;
        }

        allocations.put(key, new ZoneAllocation(
                key, vao, geometryVbo, shadingVbo, normalVbo, ibo,
                fingerprints.geometry(), fingerprints.shading(),
                normalStreamEnabled ? fingerprints.normals() : 0L,
                fingerprints.indices()));
        dirtyZonesUploadedCount++;
    }

    private static void setupVao(int vao, int geometryVbo, int shadingVbo,
                                 int normalVbo, int ibo) {
        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo);
        int geometryStride = NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, geometryStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, geometryStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, shadingVbo);
        int shadingStride = NativeSceneVertexLayout.SHADING_FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(2, 1, GL_FLOAT, false, shadingStride, 0L);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, shadingStride, 1L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, shadingStride, 2L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 3, GL_FLOAT, false, shadingStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, shadingStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(6);

        if (normalVbo != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, normalVbo);
            int normalStride = NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX * Float.BYTES;
            glVertexAttribPointer(7, 4, GL_FLOAT, false, normalStride, 0L);
            glEnableVertexAttribArray(7);
        }

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void uploadGeometry(int geometryVbo, List<GpuSceneVertex> vertices) {
        FloatBuffer data = uploadScratch.vertices(
                vertices.size() * NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            data.put(vertex.x()).put(vertex.y()).put(vertex.z())
                    .put(vertex.u()).put(vertex.v());
        }
        data.flip();

        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void uploadShading(int shadingVbo, List<GpuSceneVertex> vertices) {
        FloatBuffer data = uploadScratch.vertices(
                vertices.size() * NativeSceneVertexLayout.SHADING_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            int rgb = vertex.colorEncoding() == GpuColorEncoding.PACKED_JAGEX_HSL
                    ? OsrsTerrainColorMath.packedHslToRgb(vertex.encodedColor(), 0.6)
                    : 0;
            data.put(vertex.encodedColor())
                    .put(vertex.alpha())
                    .put(vertex.renderType())
                    .put(((rgb >>> 16) & 0xFF) / 255.0f)
                    .put(((rgb >>> 8) & 0xFF) / 255.0f)
                    .put((rgb & 0xFF) / 255.0f)
                    .put((float) vertex.priority());
        }
        data.flip();

        glBindBuffer(GL_ARRAY_BUFFER, shadingVbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void uploadNormals(int normalVbo, List<GpuSceneVertex> vertices) {
        if (normalVbo == 0) {
            throw new IllegalStateException("Normal stream upload requested without a normal VBO");
        }
        FloatBuffer data = uploadScratch.vertices(
                vertices.size() * NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            data.put((float) vertex.normalX())
                    .put((float) vertex.normalY())
                    .put((float) vertex.normalZ())
                    .put((float) vertex.normalMagnitude());
        }
        data.flip();

        glBindBuffer(GL_ARRAY_BUFFER, normalVbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void uploadIndices(int vao, int ibo, List<Integer> indices) {
        IntBuffer data = uploadScratch.indices(indices.size());
        indices.forEach(data::put);
        data.flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glBindVertexArray(0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void removeInactive(Set<Long> activeZones) {
        allocations.keySet().removeIf(key -> {
            if (activeZones.contains(key)) return false;
            ZoneAllocation allocation = allocations.get(key);
            if (allocation != null) delete(allocation);
            return true;
        });
    }

    private static void delete(ZoneAllocation allocation) {
        glDeleteVertexArrays(allocation.vao());
        glDeleteBuffers(allocation.geometryVbo());
        glDeleteBuffers(allocation.shadingVbo());
        if (allocation.normalVbo() != 0) glDeleteBuffers(allocation.normalVbo());
        glDeleteBuffers(allocation.ibo());
    }

    private void resetUploadMetrics() {
        dirtyZonesUploadedCount = 0;
        reusedAllocationsCount = 0;
        geometryStreamUploads = 0;
        shadingStreamUploads = 0;
        indexStreamUploads = 0;
        normalStreamUploads = 0;
    }

    public int localFirstIndex(int commandIndex) {
        return commandLocalFirstIndices[commandIndex];
    }

    public long zoneKeyForCommand(int commandIndex) {
        return commandZoneKeys[commandIndex];
    }

    public ZoneAllocation allocation(long zoneKey) {
        return allocations.get(zoneKey);
    }

    public int dirtyZonesUploadedCount() {
        return dirtyZonesUploadedCount;
    }

    public int totalZonesCount() {
        return totalZonesCount;
    }

    public int reusedAllocationsCount() {
        return reusedAllocationsCount;
    }

    int geometryStreamUploads() {
        return geometryStreamUploads;
    }

    int shadingStreamUploads() {
        return shadingStreamUploads;
    }

    int indexStreamUploads() {
        return indexStreamUploads;
    }

    int normalStreamUploads() {
        return normalStreamUploads;
    }

    boolean normalStreamEnabled() {
        return normalStreamEnabled;
    }

    int stagingVertexCapacityFloats() {
        return uploadScratch.vertexCapacityFloats();
    }

    int stagingIndexCapacity() {
        return uploadScratch.indexCapacity();
    }

    int stagingVertexGrowths() {
        return uploadScratch.vertexGrowths();
    }

    int stagingIndexGrowths() {
        return uploadScratch.indexGrowths();
    }

    @Override
    public void close() {
        for (ZoneAllocation allocation : allocations.values()) {
            delete(allocation);
        }
        allocations.clear();
        commandLocalFirstIndices = new int[0];
        commandZoneKeys = new long[0];
        resetUploadMetrics();
        totalZonesCount = 0;
        uploadScratch.close();
    }
}
