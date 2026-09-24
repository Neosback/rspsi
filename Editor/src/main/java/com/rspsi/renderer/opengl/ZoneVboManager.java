package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZoneStreamFingerprints;
import com.rspsi.editor.render.GpuZoneUpload;
import com.rspsi.editor.render.GpuZonedDrawCommand;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlanBuilder;
import com.rspsi.editor.render.WorldZoneCoordinate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages 8x8 logical-zone residency inside one shared GPU geometry arena.
 *
 * <p>The editor still invalidates and rebuilds geometry by 8x8 zone, but all
 * resident zones share one VAO, vertex streams and index buffer. Dirty zones
 * update only their own slices. This removes hundreds of per-zone GL objects
 * and allows ordered multi-draw batches to span zones without changing OSRS
 * draw ordering or editor invalidation semantics.</p>
 */
public final class ZoneVboManager implements AutoCloseable {
    public static final int ZONE_SIZE = 8;
    private static final int SLOT_ALIGNMENT = 64;

    public record ZoneAllocation(
            long zoneKey,
            int vao,
            int geometryVbo,
            int vertexShadingVbo,
            int faceMetadataVbo,
            int normalVbo,
            int pickerVbo,
            int ibo,
            long geometryFingerprint,
            long vertexShadingFingerprint,
            long faceMetadataFingerprint,
            long normalFingerprint,
            long pickerFingerprint,
            long indexFingerprint,
            int vertexOffset,
            int vertexCapacity,
            int indexOffset,
            int indexCapacity,
            int vertexCount,
            int indexCount
    ) {
        /** Compatibility constructor from the previous single-shading-stream layout. */
        ZoneAllocation(long zoneKey, int vao, int geometryVbo, int shadingVbo, int ibo,
                       long geometryFingerprint, long shadingFingerprint, long indexFingerprint) {
            this(zoneKey, vao, geometryVbo, shadingVbo, 0, 0, 0, ibo,
                    geometryFingerprint, shadingFingerprint, 0L, 0L, 0L, indexFingerprint,
                    0, 0, 0, 0, 0, 0);
        }

        /** Compatibility constructor from the optional-normal single-shading layout. */
        ZoneAllocation(long zoneKey, int vao, int geometryVbo, int shadingVbo,
                       int normalVbo, int ibo,
                       long geometryFingerprint, long shadingFingerprint,
                       long normalFingerprint, long indexFingerprint) {
            this(zoneKey, vao, geometryVbo, shadingVbo, 0, normalVbo, 0, ibo,
                    geometryFingerprint, shadingFingerprint, 0L,
                    normalFingerprint, 0L, indexFingerprint,
                    0, 0, 0, 0, 0, 0);
        }

        /** Compatibility constructor from the former per-zone full layout. */
        ZoneAllocation(long zoneKey, int vao, int geometryVbo, int vertexShadingVbo,
                       int faceMetadataVbo, int normalVbo, int pickerVbo, int ibo,
                       long geometryFingerprint, long vertexShadingFingerprint,
                       long faceMetadataFingerprint, long normalFingerprint,
                       long pickerFingerprint, long indexFingerprint) {
            this(zoneKey, vao, geometryVbo, vertexShadingVbo, faceMetadataVbo,
                    normalVbo, pickerVbo, ibo,
                    geometryFingerprint, vertexShadingFingerprint,
                    faceMetadataFingerprint, normalFingerprint,
                    pickerFingerprint, indexFingerprint,
                    0, 0, 0, 0, 0, 0);
        }

        ZoneAllocation withFingerprints(GpuZoneStreamFingerprints fingerprints,
                                        boolean normalsEnabled,
                                        boolean pickersEnabled,
                                        int newVertexCount,
                                        int newIndexCount) {
            return new ZoneAllocation(
                    zoneKey, vao, geometryVbo, vertexShadingVbo, faceMetadataVbo,
                    normalVbo, pickerVbo, ibo,
                    fingerprints.geometry(), fingerprints.vertexShading(),
                    fingerprints.faceShading(),
                    normalsEnabled ? fingerprints.normals() : 0L,
                    pickersEnabled ? fingerprints.pickerIds() : 0L,
                    fingerprints.indices(),
                    vertexOffset, vertexCapacity, indexOffset, indexCapacity,
                    newVertexCount, newIndexCount);
        }
    }

    record StreamUploadDecision(boolean geometry, boolean vertexShading,
                                boolean faceMetadata, boolean normals,
                                boolean pickers, boolean indices) {
        boolean shading() {
            return vertexShading || faceMetadata;
        }

        boolean any() {
            return geometry || vertexShading || faceMetadata || normals || pickers || indices;
        }
    }

    static StreamUploadDecision streamUploadDecision(
            ZoneAllocation existing, GpuZoneStreamFingerprints fingerprints) {
        return streamUploadDecision(existing, fingerprints, false, false);
    }

    static StreamUploadDecision streamUploadDecision(
            ZoneAllocation existing, GpuZoneStreamFingerprints fingerprints,
            boolean normalStreamEnabled) {
        return streamUploadDecision(existing, fingerprints, normalStreamEnabled, false);
    }

    static StreamUploadDecision streamUploadDecision(
            ZoneAllocation existing, GpuZoneStreamFingerprints fingerprints,
            boolean normalStreamEnabled, boolean pickerStreamEnabled) {
        if (fingerprints == null) {
            throw new IllegalArgumentException("Zone stream fingerprints cannot be null");
        }
        return new StreamUploadDecision(
                existing == null || existing.geometryFingerprint() != fingerprints.geometry(),
                existing == null
                        || existing.vertexShadingFingerprint() != fingerprints.vertexShading(),
                existing == null
                        || existing.faceMetadataFingerprint() != fingerprints.faceShading(),
                normalStreamEnabled && (existing == null
                        || existing.normalFingerprint() != fingerprints.normals()),
                pickerStreamEnabled && (existing == null
                        || existing.pickerFingerprint() != fingerprints.pickerIds()),
                existing == null || existing.indexFingerprint() != fingerprints.indices());
    }

    private final Map<Long, ZoneAllocation> allocations = new HashMap<>();
    private final SharedGpuArena arena = new SharedGpuArena();
    private final GpuZonedUploadPlanBuilder fallbackBuilder = new GpuZonedUploadPlanBuilder();

    private boolean normalStreamEnabled;
    private boolean pickerStreamEnabled;
    private int[] commandFirstIndices = new int[0];
    private long[] commandZoneKeys = new long[0];

    private int dirtyZonesUploadedCount;
    private int reusedAllocationsCount;
    private int totalZonesCount;
    private int geometryStreamUploads;
    private int vertexShadingStreamUploads;
    private int faceMetadataStreamUploads;
    private int indexStreamUploads;
    private int normalStreamUploads;
    private int pickerStreamUploads;
    private int removedAllocationsCount;
    private long geometryBytesUploaded;
    private long vertexShadingBytesUploaded;
    private long faceMetadataBytesUploaded;
    private long normalBytesUploaded;
    private long pickerBytesUploaded;
    private long indexBytesUploaded;

    public ZoneVboManager() {
        this(false, false);
    }

    ZoneVboManager(boolean normalStreamEnabled) {
        this(normalStreamEnabled, false);
    }

    ZoneVboManager(boolean normalStreamEnabled, boolean pickerStreamEnabled) {
        this.normalStreamEnabled = normalStreamEnabled;
        this.pickerStreamEnabled = pickerStreamEnabled;
    }

    boolean setNormalStreamEnabled(boolean enabled) {
        return setAuxiliaryStreams(enabled, pickerStreamEnabled);
    }

    boolean setPickerStreamEnabled(boolean enabled) {
        return setAuxiliaryStreams(normalStreamEnabled, enabled);
    }

    boolean setAuxiliaryStreams(boolean normalsEnabled, boolean pickersEnabled) {
        if (normalStreamEnabled == normalsEnabled
                && pickerStreamEnabled == pickersEnabled) {
            return false;
        }
        normalStreamEnabled = normalsEnabled;
        pickerStreamEnabled = pickersEnabled;
        clearAllocations();
        return true;
    }

    public static long zoneKey(WorldTileAddress tile) {
        return WorldZoneCoordinate.from(tile).key();
    }

    /** Compatibility path. Production normally supplies a pre-zoned plan. */
    public void upload(GpuUploadPlan plan) {
        if (plan == null || plan.commands().isEmpty() || plan.vertices().isEmpty()) {
            resetUploadMetrics();
            clearAllocations();
            return;
        }
        upload(fallbackBuilder.build(plan));
    }

    public void upload(GpuZonedUploadPlan plan) {
        resetUploadMetrics();
        if (plan == null || plan.zones().isEmpty() || plan.commandRefs().isEmpty()) {
            clearAllocations();
            return;
        }

        totalZonesCount = plan.zones().size();
        boolean rebuild = requiresArenaRebuild(plan);
        if (rebuild) {
            rebuildArena(plan);
        } else {
            updateDirtyZones(plan);
        }
        bindCommandOffsets(plan);
    }

    private boolean requiresArenaRebuild(GpuZonedUploadPlan plan) {
        if (!arena.allocated()
                || arena.normalsEnabled() != normalStreamEnabled
                || arena.pickersEnabled() != pickerStreamEnabled
                || allocations.size() != plan.zones().size()) {
            return true;
        }

        for (GpuZoneUpload zone : plan.zones().values()) {
            ZoneAllocation allocation = allocations.get(zone.zone().key());
            if (allocation == null
                    || zone.vertices().size() > allocation.vertexCapacity()
                    || zone.indices().size() > allocation.indexCapacity()) {
                return true;
            }
        }
        return false;
    }

    private void rebuildArena(GpuZonedUploadPlan plan) {
        int removed = allocations.size();
        allocations.clear();

        List<GpuZoneUpload> zones = plan.zones().values().stream()
                .sorted(Comparator.comparing(GpuZoneUpload::zone))
                .toList();

        int totalVertexCapacity = 0;
        int totalIndexCapacity = 0;
        for (GpuZoneUpload zone : zones) {
            totalVertexCapacity = Math.addExact(totalVertexCapacity,
                    slotCapacity(zone.vertices().size()));
            totalIndexCapacity = Math.addExact(totalIndexCapacity,
                    slotCapacity(zone.indices().size()));
        }

        arena.allocate(totalVertexCapacity, totalIndexCapacity,
                normalStreamEnabled, pickerStreamEnabled);

        int vertexOffset = 0;
        int indexOffset = 0;
        for (GpuZoneUpload zone : zones) {
            int vertexCapacity = slotCapacity(zone.vertices().size());
            int indexCapacity = slotCapacity(zone.indices().size());
            GpuZoneStreamFingerprints fp = zone.fingerprints();
            long key = zone.zone().key();

            ZoneAllocation allocation = new ZoneAllocation(
                    key,
                    arena.vao(),
                    arena.geometryVbo(),
                    arena.vertexShadingVbo(),
                    arena.faceMetadataVbo(),
                    arena.normalVbo(),
                    arena.pickerVbo(),
                    arena.ibo(),
                    0L, 0L, 0L, 0L, 0L, 0L,
                    vertexOffset, vertexCapacity,
                    indexOffset, indexCapacity,
                    zone.vertices().size(), zone.indices().size());

            uploadStreams(zone, allocation,
                    new StreamUploadDecision(
                            true, true, true,
                            normalStreamEnabled, pickerStreamEnabled, true));

            allocation = allocation.withFingerprints(
                    fp, normalStreamEnabled, pickerStreamEnabled,
                    zone.vertices().size(), zone.indices().size());
            allocations.put(key, allocation);

            vertexOffset += vertexCapacity;
            indexOffset += indexCapacity;
            dirtyZonesUploadedCount++;
        }

        removedAllocationsCount = Math.max(0, removed - allocations.size());
    }

    private void updateDirtyZones(GpuZonedUploadPlan plan) {
        for (GpuZoneUpload zone : plan.zones().values()) {
            long key = zone.zone().key();
            ZoneAllocation existing = allocations.get(key);
            StreamUploadDecision decision = streamUploadDecision(
                    existing, zone.fingerprints(),
                    normalStreamEnabled, pickerStreamEnabled);
            if (!decision.any()) {
                reusedAllocationsCount++;
                continue;
            }

            uploadStreams(zone, existing, decision);
            allocations.put(key, existing.withFingerprints(
                    zone.fingerprints(),
                    normalStreamEnabled,
                    pickerStreamEnabled,
                    zone.vertices().size(),
                    zone.indices().size()));
            dirtyZonesUploadedCount++;
        }
    }

    private void uploadStreams(GpuZoneUpload zone,
                               ZoneAllocation allocation,
                               StreamUploadDecision decision) {
        List<com.rspsi.editor.render.GpuSceneVertex> vertices = zone.vertices();
        List<Integer> indices = zone.indices();

        if (decision.geometry()) {
            arena.uploadGeometry(allocation.vertexOffset(), vertices);
            geometryStreamUploads++;
            geometryBytesUploaded += (long) vertices.size()
                    * NativeSceneVertexLayout.GEOMETRY_FLOATS_PER_VERTEX * Float.BYTES;
        }
        if (decision.vertexShading()) {
            arena.uploadVertexShading(allocation.vertexOffset(), vertices);
            vertexShadingStreamUploads++;
            vertexShadingBytesUploaded += (long) vertices.size()
                    * NativeSceneVertexLayout.VERTEX_SHADING_FLOATS_PER_VERTEX * Float.BYTES;
        }
        if (decision.faceMetadata()) {
            arena.uploadFaceMetadata(allocation.vertexOffset(), vertices);
            faceMetadataStreamUploads++;
            faceMetadataBytesUploaded += (long) vertices.size()
                    * NativeSceneVertexLayout.FACE_METADATA_FLOATS_PER_VERTEX * Float.BYTES;
        }
        if (decision.normals()) {
            arena.uploadNormals(allocation.vertexOffset(), vertices);
            normalStreamUploads++;
            normalBytesUploaded += (long) vertices.size()
                    * NativeSceneVertexLayout.NORMAL_FLOATS_PER_VERTEX * Float.BYTES;
        }
        if (decision.pickers()) {
            arena.uploadPickerIds(allocation.vertexOffset(), vertices);
            pickerStreamUploads++;
            pickerBytesUploaded += (long) vertices.size() * Integer.BYTES;
        }
        if (decision.indices()) {
            arena.uploadIndices(allocation.indexOffset(), allocation.vertexOffset(), zone);
            indexStreamUploads++;
            indexBytesUploaded += (long) indices.size() * Integer.BYTES;
        }
    }

    private void bindCommandOffsets(GpuZonedUploadPlan plan) {
        List<GpuZonedDrawCommand> refs = plan.commandRefs();
        if (commandFirstIndices.length < refs.size()) {
            commandFirstIndices = new int[refs.size()];
            commandZoneKeys = new long[refs.size()];
        }

        for (int index = 0; index < refs.size(); index++) {
            GpuZonedDrawCommand ref = refs.get(index);
            long zoneKey = ref.zone().key();
            ZoneAllocation allocation = allocations.get(zoneKey);
            if (allocation == null) {
                throw new IllegalStateException("Missing shared arena zone allocation " + ref.zone());
            }
            commandFirstIndices[index] =
                    allocation.indexOffset() + ref.localFirstIndex();
            commandZoneKeys[index] = zoneKey;
        }
    }

    static int slotCapacity(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("Zone slot requirement cannot be negative");
        }
        if (required == 0) return SLOT_ALIGNMENT;
        int slack = Math.max(16, required >>> 3);
        int wanted = Math.addExact(required, slack);
        int remainder = wanted % SLOT_ALIGNMENT;
        return remainder == 0 ? wanted : Math.addExact(wanted, SLOT_ALIGNMENT - remainder);
    }

    private void resetUploadMetrics() {
        dirtyZonesUploadedCount = 0;
        reusedAllocationsCount = 0;
        geometryStreamUploads = 0;
        vertexShadingStreamUploads = 0;
        faceMetadataStreamUploads = 0;
        indexStreamUploads = 0;
        normalStreamUploads = 0;
        pickerStreamUploads = 0;
        removedAllocationsCount = 0;
        geometryBytesUploaded = 0L;
        vertexShadingBytesUploaded = 0L;
        faceMetadataBytesUploaded = 0L;
        normalBytesUploaded = 0L;
        pickerBytesUploaded = 0L;
        indexBytesUploaded = 0L;
    }

    /**
     * Returns the global first index inside the shared IBO for one command.
     * The historical method name is retained to avoid widening this backend
     * change into the renderer-neutral contract.
     */
    public int localFirstIndex(int commandIndex) {
        return commandFirstIndices[commandIndex];
    }

    public long zoneKeyForCommand(int commandIndex) {
        return commandZoneKeys[commandIndex];
    }

    /** All commands in the shared arena can participate in the same native batch. */
    public long drawGroupKeyForCommand(int commandIndex) {
        ZoneAllocation allocation = allocationForCommand(commandIndex);
        return allocation == null ? Long.MIN_VALUE : Integer.toUnsignedLong(allocation.vao());
    }

    public ZoneAllocation allocationForCommand(int commandIndex) {
        return allocations.get(zoneKeyForCommand(commandIndex));
    }

    public ZoneAllocation allocation(long zoneKey) {
        return allocations.get(zoneKey);
    }

    public int dirtyZonesUploadedCount() { return dirtyZonesUploadedCount; }
    public int totalZonesCount() { return totalZonesCount; }
    public int reusedAllocationsCount() { return reusedAllocationsCount; }

    int geometryStreamUploads() { return geometryStreamUploads; }
    int shadingStreamUploads() { return vertexShadingStreamUploads + faceMetadataStreamUploads; }
    int vertexShadingStreamUploads() { return vertexShadingStreamUploads; }
    int faceMetadataStreamUploads() { return faceMetadataStreamUploads; }
    int indexStreamUploads() { return indexStreamUploads; }
    int normalStreamUploads() { return normalStreamUploads; }
    int pickerStreamUploads() { return pickerStreamUploads; }
    int removedAllocationsCount() { return removedAllocationsCount; }

    long geometryBytesUploaded() { return geometryBytesUploaded; }
    long vertexShadingBytesUploaded() { return vertexShadingBytesUploaded; }
    long faceMetadataBytesUploaded() { return faceMetadataBytesUploaded; }
    long normalBytesUploaded() { return normalBytesUploaded; }
    long pickerBytesUploaded() { return pickerBytesUploaded; }
    long indexBytesUploaded() { return indexBytesUploaded; }

    long totalBytesUploaded() {
        return geometryBytesUploaded + vertexShadingBytesUploaded
                + faceMetadataBytesUploaded + normalBytesUploaded
                + pickerBytesUploaded + indexBytesUploaded;
    }

    boolean normalStreamEnabled() { return normalStreamEnabled; }
    boolean pickerStreamEnabled() { return pickerStreamEnabled; }

    int stagingVertexCapacityFloats() { return arena.stagingVertexCapacityFloats(); }
    int stagingIndexCapacity() { return arena.stagingIndexCapacity(); }
    int stagingVertexGrowths() { return arena.stagingVertexGrowths(); }
    int stagingIndexGrowths() { return arena.stagingIndexGrowths(); }

    int residentVaoCount() {
        return arena.allocated() ? 1 : 0;
    }

    int residentBufferCount() {
        if (!arena.allocated()) return 0;
        return 4 + (normalStreamEnabled ? 1 : 0) + (pickerStreamEnabled ? 1 : 0);
    }

    private void clearAllocations() {
        allocations.clear();
        arena.reset();
        commandFirstIndices = new int[0];
        commandZoneKeys = new long[0];
        totalZonesCount = 0;
    }

    @Override
    public void close() {
        allocations.clear();
        commandFirstIndices = new int[0];
        commandZoneKeys = new long[0];
        totalZonesCount = 0;
        arena.close();
    }
}
