package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZoneUpload;
import com.rspsi.editor.render.GpuZonedDrawCommand;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.WorldZoneCoordinate;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

/**
 * Manages 8x8 zone-partitioned GPU vertex and index buffers.
 *
 * <p>Rather than re-uploading the entire world geometry on every terrain or object edit,
 * geometry is partitioned into 8x8 zone partitions matching {@code SceneZone}. Edits
 * only re-upload the modified 8x8 zones into their dedicated GPU VBOs while static zones
 * remain resident in GPU memory.</p>
 */
public final class ZoneVboManager implements AutoCloseable {
    public static final int ZONE_SIZE = 8;
    private static final int FLOATS_PER_VERTEX = 12;

    public record ZoneAllocation(long zoneKey, int vao, int vbo, int ibo, long fingerprint) { }

    private final Map<Long, ZoneAllocation> allocations = new HashMap<>();
    private int[] commandLocalFirstIndices = new int[0];
    private long[] commandZoneKeys = new long[0];
    private int dirtyZonesUploadedCount = 0;
    private int reusedAllocationsCount = 0;
    private int totalZonesCount = 0;

    public static long zoneKey(WorldTileAddress tile) {
        return WorldZoneCoordinate.from(tile).key();
    }

    public void upload(GpuUploadPlan plan) {
        dirtyZonesUploadedCount = 0;
        reusedAllocationsCount = 0;
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

        // Group commands by 8x8 zone
        Map<Long, List<Integer>> zoneToCommandIndices = new HashMap<>();
        for (int i = 0; i < commands.size(); i++) {
            GpuDrawCommand command = commands.get(i);
            long key = zoneKey(command.tile());
            commandZoneKeys[i] = key;
            zoneToCommandIndices.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        totalZonesCount = zoneToCommandIndices.size();
        Set<Long> activeZones = new HashSet<>(zoneToCommandIndices.keySet());

        // Fast global-to-local vertex index map
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
                    int gIdx = plan.indices().get(cmd.firstIndex() + i);
                    int lIdx = globalToLocal[gIdx];
                    if (lIdx == -1) {
                        lIdx = localVertices.size();
                        localVertices.add(plan.vertices().get(gIdx));
                        globalToLocal[gIdx] = lIdx;
                        touchedGlobal.add(gIdx);
                    }
                    localIndices.add(lIdx);
                }
            }

            // Reset touched entries for next zone
            for (int gIdx : touchedGlobal) {
                globalToLocal[gIdx] = -1;
            }
            touchedGlobal.clear();

            // Compute zone fingerprint
            long fingerprint = GpuZoneUpload.fingerprint(localVertices, localIndices);
            ZoneAllocation existing = allocations.get(key);

            if (existing != null && existing.fingerprint() == fingerprint) {
                // Buffer is already up to date on GPU
                reusedAllocationsCount++;
                continue;
            }

            // Need to upload or update this zone's GPU buffers
            int vao, vbo, ibo;
            if (existing == null) {
                vao = glGenVertexArrays();
                vbo = glGenBuffers();
                ibo = glGenBuffers();
                setupVao(vao, vbo, ibo);
            } else {
                vao = existing.vao();
                vbo = existing.vbo();
                ibo = existing.ibo();
            }

            uploadZoneBuffers(vao, vbo, ibo, localVertices, localIndices);
            allocations.put(key, new ZoneAllocation(key, vao, vbo, ibo, fingerprint));
            dirtyZonesUploadedCount++;
        }

        // Clean up allocations for zones that no longer exist in the plan
        allocations.keySet().removeIf(key -> {
            if (!activeZones.contains(key)) {
                ZoneAllocation alloc = allocations.get(key);
                if (alloc != null) {
                    glDeleteVertexArrays(alloc.vao());
                    glDeleteBuffers(alloc.vbo());
                    glDeleteBuffers(alloc.ibo());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Uploads a pre-partitioned native plan without scanning or copying the
     * global flat vertex/index arrays on the render thread.
     */
    public void upload(GpuZonedUploadPlan plan) {
        dirtyZonesUploadedCount = 0;
        reusedAllocationsCount = 0;
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
            ZoneAllocation existing = allocations.get(key);
            if (existing != null && existing.fingerprint() == zone.fingerprint()) {
                reusedAllocationsCount++;
                continue;
            }

            int vao;
            int vbo;
            int ibo;
            if (existing == null) {
                vao = glGenVertexArrays();
                vbo = glGenBuffers();
                ibo = glGenBuffers();
                setupVao(vao, vbo, ibo);
            } else {
                vao = existing.vao();
                vbo = existing.vbo();
                ibo = existing.ibo();
            }
            uploadZoneBuffers(vao, vbo, ibo, zone.vertices(), zone.indices());
            allocations.put(key, new ZoneAllocation(key, vao, vbo, ibo, zone.fingerprint()));
            dirtyZonesUploadedCount++;
        }

        allocations.keySet().removeIf(key -> {
            if (activeZones.contains(key)) return false;
            ZoneAllocation allocation = allocations.get(key);
            if (allocation != null) {
                glDeleteVertexArrays(allocation.vao());
                glDeleteBuffers(allocation.vbo());
                glDeleteBuffers(allocation.ibo());
            }
            return true;
        });
    }

    private static void setupVao(int vao, int vbo, int ibo) {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 3, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 11L * Float.BYTES);
        glEnableVertexAttribArray(6);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void uploadZoneBuffers(int vao, int vbo, int ibo,
                                          List<GpuSceneVertex> vertices,
                                          List<Integer> indices) {
        FloatBuffer vertexData = BufferUtils.createFloatBuffer(vertices.size() * FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : vertices) {
            int rgb = vertex.colorEncoding() == GpuColorEncoding.PACKED_JAGEX_HSL
                    ? OsrsTerrainColorMath.packedHslToRgb(vertex.encodedColor(), 0.6)
                    : 0;
            vertexData.put(vertex.x()).put(vertex.y()).put(vertex.z())
                    .put(vertex.u()).put(vertex.v()).put(vertex.encodedColor())
                    .put(vertex.alpha()).put(vertex.renderType())
                    .put(((rgb >>> 16) & 0xFF) / 255.0f)
                    .put(((rgb >>> 8) & 0xFF) / 255.0f)
                    .put((rgb & 0xFF) / 255.0f)
                    .put((float) vertex.priority());
        }
        vertexData.flip();

        IntBuffer indexData = BufferUtils.createIntBuffer(indices.size());
        indices.forEach(indexData::put);
        indexData.flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
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

    @Override
    public void close() {
        for (ZoneAllocation alloc : allocations.values()) {
            glDeleteVertexArrays(alloc.vao());
            glDeleteBuffers(alloc.vbo());
            glDeleteBuffers(alloc.ibo());
        }
        allocations.clear();
        commandLocalFirstIndices = new int[0];
        commandZoneKeys = new long[0];
        dirtyZonesUploadedCount = 0;
        reusedAllocationsCount = 0;
        totalZonesCount = 0;
    }
}
