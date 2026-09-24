package com.rspsi.editor.render.picker;

import com.rspsi.editor.render.ClientModelBounds;
import com.rspsi.editor.render.ClientRenderablePlacement;
import com.rspsi.editor.render.GameObjectSceneMetadata;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZoneUpload;
import com.rspsi.editor.render.GpuZonedDrawCommand;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlanBuilder;
import com.rspsi.editor.render.WorldZoneCoordinate;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Incremental CPU spatial index for scene picking.
 *
 * <p>The renderer already retains immutable geometry by 8x8 world zone. This
 * index follows that ownership boundary, but stores triangle metadata in flat
 * primitive arrays rather than one Java object per triangle. Spatial buckets
 * retain packed long handles, so large scenes do not create hundreds of
 * thousands of TriangleRef objects plus duplicate reference arrays.</p>
 */
final class PickingSpatialIndex {
    static final int ZONE_TILES = 8;
    static final int PLANE_COUNT = 4;
    static final long NO_TRIANGLE = -1L;

    private final GpuZonedUploadPlanBuilder fallbackBuilder = new GpuZonedUploadPlanBuilder();

    private GpuUploadPlan fallbackPlan;
    private GpuZonedUploadPlan fallbackZonedPlan;

    private GpuUploadPlan currentPlan;
    private GpuZonedUploadPlan currentZonedPlan;
    private Map<WorldZoneCoordinate, SourceZone> sources = Map.of();
    private Map<ZoneKey, PickingZone> zones = Map.of();
    private Snapshot snapshot = Snapshot.empty();
    private Metrics lastMetrics = Metrics.empty();
    private int generation;

    synchronized Snapshot indexFor(GpuUploadPlan plan, GpuZonedUploadPlan candidate) {
        Objects.requireNonNull(plan, "plan");
        GpuZonedUploadPlan zonedPlan = resolveZonedPlan(plan, candidate);
        if (plan == currentPlan && zonedPlan == currentZonedPlan) {
            return snapshot;
        }

        Map<WorldZoneCoordinate, SourceZone> nextSources = new LinkedHashMap<>();
        Set<ZoneKey> dirtySpatialZones = new LinkedHashSet<>();
        int rebuiltSources = 0;
        int reusedSources = 0;

        for (Map.Entry<WorldZoneCoordinate, GpuZoneUpload> entry
                : zonedPlan.zones().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            WorldZoneCoordinate coordinate = entry.getKey();
            GpuZoneUpload upload = entry.getValue();
            SourceZone previous = sources.get(coordinate);
            SourceZone source;
            if (previous != null && previous.upload() == upload) {
                source = previous;
                reusedSources++;
            } else {
                source = SourceZone.build(upload);
                rebuiltSources++;
                if (previous != null) dirtySpatialZones.addAll(previous.coverage());
                dirtySpatialZones.addAll(source.coverage());
            }
            nextSources.put(coordinate, source);
        }

        for (Map.Entry<WorldZoneCoordinate, SourceZone> entry : sources.entrySet()) {
            if (!nextSources.containsKey(entry.getKey())) {
                dirtySpatialZones.addAll(entry.getValue().coverage());
            }
        }

        boolean sourceSetChanged = sources.size() != nextSources.size()
                || !sources.keySet().equals(nextSources.keySet());

        int ordinal = 0;
        for (SourceZone source : nextSources.values()) {
            source.bindSnapshotOrdinal(ordinal++);
        }

        bindGlobalOrder(zonedPlan, nextSources);

        Set<ZoneKey> requiredSpatialZones = new TreeSet<>();
        for (SourceZone source : nextSources.values()) {
            requiredSpatialZones.addAll(source.coverage());
        }

        /*
         * Bucket handles encode the source ordinal in their high 32 bits.
         * Ordinals stay stable during ordinary dirty-zone rebuilds. If source
         * zones are added/removed, rebuild every spatial bucket once so no
         * retained handle can point at a shifted source slot.
         */
        if (sourceSetChanged) {
            dirtySpatialZones.addAll(requiredSpatialZones);
        }

        Map<ZoneKey, PickingZone> nextZones = new LinkedHashMap<>();
        int rebuiltZones = 0;
        int reusedZones = 0;
        for (ZoneKey coordinate : requiredSpatialZones) {
            PickingZone previous = zones.get(coordinate);
            if (previous != null && !dirtySpatialZones.contains(coordinate)) {
                nextZones.put(coordinate, previous);
                reusedZones++;
            } else {
                nextZones.put(coordinate, PickingZone.build(coordinate, nextSources.values()));
                rebuiltZones++;
            }
        }

        sources = Map.copyOf(nextSources);
        zones = Map.copyOf(nextZones);
        snapshot = Snapshot.build(zones, nextSources.values());
        lastMetrics = new Metrics(rebuiltZones, reusedZones, zones.size(),
                rebuiltSources, reusedSources, sources.size());
        currentPlan = plan;
        currentZonedPlan = zonedPlan;
        return snapshot;
    }

    synchronized int beginPick() {
        if (generation == Integer.MAX_VALUE) {
            snapshot.clearTestedGenerations();
            generation = 1;
        } else {
            generation++;
        }
        return generation;
    }

    synchronized Metrics lastMetrics() {
        return lastMetrics;
    }

    private GpuZonedUploadPlan resolveZonedPlan(GpuUploadPlan plan,
                                                GpuZonedUploadPlan candidate) {
        if (candidate != null && candidate.sourceFingerprint().equals(plan.fingerprint())) {
            return candidate;
        }
        if (plan == fallbackPlan && fallbackZonedPlan != null) {
            return fallbackZonedPlan;
        }
        fallbackPlan = plan;
        fallbackZonedPlan = fallbackBuilder.build(plan);
        return fallbackZonedPlan;
    }

    private static void bindGlobalOrder(GpuZonedUploadPlan zonedPlan,
                                        Map<WorldZoneCoordinate, SourceZone> sources) {
        for (SourceZone source : sources.values()) source.beginBinding();
        for (GpuZonedDrawCommand ref : zonedPlan.commandRefs()) {
            SourceZone source = sources.get(ref.zone());
            if (source == null) {
                throw new IllegalStateException("Missing picking source zone " + ref.zone());
            }
            source.bindNext(ref.command());
        }
        for (SourceZone source : sources.values()) source.finishBinding();
    }

    record Metrics(int rebuiltZones, int reusedZones, int totalZones,
                   int rebuiltSourceZones, int reusedSourceZones, int totalSourceZones) {
        Metrics {
            if (rebuiltZones < 0 || reusedZones < 0 || totalZones < 0
                    || rebuiltSourceZones < 0 || reusedSourceZones < 0
                    || totalSourceZones < 0) {
                throw new IllegalArgumentException("Picking index metrics cannot be negative");
            }
        }

        static Metrics empty() {
            return new Metrics(0, 0, 0, 0, 0, 0);
        }
    }

    static final class Snapshot {
        private static final long[] EMPTY_BUCKET = new long[0];
        private static final Snapshot EMPTY = new Snapshot(new PickingZone[0],
                0, -1, 0, -1, 0, -1, 0, -1, 0, new SourceZone[0]);

        private final PickingZone[] denseZones;
        private final int minZoneX;
        private final int maxZoneX;
        private final int minZoneY;
        private final int maxZoneY;
        private final int minTileX;
        private final int maxTileX;
        private final int minTileY;
        private final int maxTileY;
        private final int triangleCount;
        private final SourceZone[] sources;

        private Snapshot(PickingZone[] denseZones,
                         int minZoneX, int maxZoneX, int minZoneY, int maxZoneY,
                         int minTileX, int maxTileX, int minTileY, int maxTileY,
                         int triangleCount, SourceZone[] sources) {
            this.denseZones = denseZones;
            this.minZoneX = minZoneX;
            this.maxZoneX = maxZoneX;
            this.minZoneY = minZoneY;
            this.maxZoneY = maxZoneY;
            this.minTileX = minTileX;
            this.maxTileX = maxTileX;
            this.minTileY = minTileY;
            this.maxTileY = maxTileY;
            this.triangleCount = triangleCount;
            this.sources = sources;
        }

        static Snapshot empty() {
            return EMPTY;
        }

        static Snapshot build(Map<ZoneKey, PickingZone> zones,
                              Collection<SourceZone> sourceCollection) {
            if (zones.isEmpty() || sourceCollection.isEmpty()) return empty();

            int minZoneX = Integer.MAX_VALUE;
            int maxZoneX = Integer.MIN_VALUE;
            int minZoneY = Integer.MAX_VALUE;
            int maxZoneY = Integer.MIN_VALUE;
            for (ZoneKey zone : zones.keySet()) {
                minZoneX = Math.min(minZoneX, zone.zoneX());
                maxZoneX = Math.max(maxZoneX, zone.zoneX());
                minZoneY = Math.min(minZoneY, zone.zoneY());
                maxZoneY = Math.max(maxZoneY, zone.zoneY());
            }

            int width = maxZoneX - minZoneX + 1;
            int height = maxZoneY - minZoneY + 1;
            PickingZone[] dense = new PickingZone[Math.multiplyExact(
                    PLANE_COUNT, Math.multiplyExact(width, height))];
            for (Map.Entry<ZoneKey, PickingZone> entry : zones.entrySet()) {
                ZoneKey zone = entry.getKey();
                int index = denseIndex(zone.plane(), zone.zoneX(), zone.zoneY(),
                        minZoneX, minZoneY, width, height);
                dense[index] = entry.getValue();
            }

            SourceZone[] sourceArray = new SourceZone[sourceCollection.size()];
            int minTileX = Integer.MAX_VALUE;
            int maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE;
            int maxTileY = Integer.MIN_VALUE;
            int triangles = 0;
            for (SourceZone source : sourceCollection) {
                sourceArray[source.snapshotOrdinal()] = source;
                if (source.triangleCount() == 0) continue;
                minTileX = Math.min(minTileX, source.minTileX());
                maxTileX = Math.max(maxTileX, source.maxTileX());
                minTileY = Math.min(minTileY, source.minTileY());
                maxTileY = Math.max(maxTileY, source.maxTileY());
                triangles += source.triangleCount();
            }
            if (triangles == 0) return empty();

            return new Snapshot(dense, minZoneX, maxZoneX, minZoneY, maxZoneY,
                    minTileX, maxTileX, minTileY, maxTileY, triangles, sourceArray);
        }

        long[] bucket(int plane, int tileX, int tileY) {
            if (plane < 0 || plane >= PLANE_COUNT || denseZones.length == 0) {
                return EMPTY_BUCKET;
            }
            int zoneX = Math.floorDiv(tileX, ZONE_TILES);
            int zoneY = Math.floorDiv(tileY, ZONE_TILES);
            if (zoneX < minZoneX || zoneX > maxZoneX
                    || zoneY < minZoneY || zoneY > maxZoneY) {
                return EMPTY_BUCKET;
            }
            int width = maxZoneX - minZoneX + 1;
            int height = maxZoneY - minZoneY + 1;
            PickingZone zone = denseZones[denseIndex(
                    plane, zoneX, zoneY, minZoneX, minZoneY, width, height)];
            return zone == null ? EMPTY_BUCKET : zone.bucket(tileX, tileY);
        }

        GpuDrawCommand command(long handle) {
            return source(handle).command(triangleIndex(handle));
        }

        GpuSceneVertex a(long handle) {
            return source(handle).a(triangleIndex(handle));
        }

        GpuSceneVertex b(long handle) {
            return source(handle).b(triangleIndex(handle));
        }

        GpuSceneVertex c(long handle) {
            return source(handle).c(triangleIndex(handle));
        }

        int order(long handle) {
            return source(handle).order(triangleIndex(handle));
        }

        boolean markTested(long handle, int generation) {
            return source(handle).markTested(triangleIndex(handle), generation);
        }

        byte broadPhase(long handle, int generation,
                        float ox, float oy, float oz,
                        float dx, float dy, float dz,
                        float near, float far) {
            SourceZone source = source(handle);
            return source.broadPhase(source.localCommandIndex(triangleIndex(handle)), generation,
                    ox, oy, oz, dx, dy, dz, near, far);
        }

        int minTileX() { return minTileX; }
        int maxTileX() { return maxTileX; }
        int minTileY() { return minTileY; }
        int maxTileY() { return maxTileY; }
        int triangleCount() { return triangleCount; }

        boolean isEmpty() {
            return triangleCount == 0;
        }

        void clearTestedGenerations() {
            for (SourceZone source : sources) {
                if (source != null) source.clearTestedGenerations();
            }
        }

        private SourceZone source(long handle) {
            int sourceIndex = (int) (handle >>> 32);
            if (sourceIndex < 0 || sourceIndex >= sources.length || sources[sourceIndex] == null) {
                throw new IllegalStateException("Invalid picking source handle: " + handle);
            }
            return sources[sourceIndex];
        }

        private static int triangleIndex(long handle) {
            return (int) handle;
        }

        private static int denseIndex(int plane, int zoneX, int zoneY,
                                      int minZoneX, int minZoneY,
                                      int width, int height) {
            int localX = zoneX - minZoneX;
            int localY = zoneY - minZoneY;
            return (plane * height + localY) * width + localX;
        }
    }

    private static final class SourceZone {
        private final GpuZoneUpload upload;
        private final int[] localCommandIndices;
        private final int[] vertexA;
        private final int[] vertexB;
        private final int[] vertexC;
        private final int[] faceOffsets;
        private final int[] minTriangleTileX;
        private final int[] maxTriangleTileX;
        private final int[] minTriangleTileY;
        private final int[] maxTriangleTileY;
        private final int[] testedGeneration;
        private final int[] globalFirstIndices;
        private final float[][] commandAabbs;
        private final int[] broadPhaseGeneration;
        private final byte[] broadPhaseResult;
        private final Set<ZoneKey> coverage;
        private final int minTileX;
        private final int maxTileX;
        private final int minTileY;
        private final int maxTileY;
        private int bindingCursor;
        private int snapshotOrdinal;

        private SourceZone(GpuZoneUpload upload,
                           int[] localCommandIndices,
                           int[] vertexA, int[] vertexB, int[] vertexC,
                           int[] faceOffsets,
                           int[] minTriangleTileX, int[] maxTriangleTileX,
                           int[] minTriangleTileY, int[] maxTriangleTileY,
                           int[] testedGeneration,
                           int[] globalFirstIndices,
                           float[][] commandAabbs,
                           int[] broadPhaseGeneration,
                           byte[] broadPhaseResult,
                           Set<ZoneKey> coverage,
                           int minTileX, int maxTileX, int minTileY, int maxTileY) {
            this.upload = upload;
            this.localCommandIndices = localCommandIndices;
            this.vertexA = vertexA;
            this.vertexB = vertexB;
            this.vertexC = vertexC;
            this.faceOffsets = faceOffsets;
            this.minTriangleTileX = minTriangleTileX;
            this.maxTriangleTileX = maxTriangleTileX;
            this.minTriangleTileY = minTriangleTileY;
            this.maxTriangleTileY = maxTriangleTileY;
            this.testedGeneration = testedGeneration;
            this.globalFirstIndices = globalFirstIndices;
            this.commandAabbs = commandAabbs;
            this.broadPhaseGeneration = broadPhaseGeneration;
            this.broadPhaseResult = broadPhaseResult;
            this.coverage = coverage;
            this.minTileX = minTileX;
            this.maxTileX = maxTileX;
            this.minTileY = minTileY;
            this.maxTileY = maxTileY;
        }

        static SourceZone build(GpuZoneUpload upload) {
            int triangleCount = 0;
            for (GpuDrawCommand command : upload.commands()) {
                triangleCount += command.indexCount() / 3;
            }

            int[] localCommandIndices = new int[triangleCount];
            int[] vertexA = new int[triangleCount];
            int[] vertexB = new int[triangleCount];
            int[] vertexC = new int[triangleCount];
            int[] faceOffsets = new int[triangleCount];
            int[] minTriangleTileX = new int[triangleCount];
            int[] maxTriangleTileX = new int[triangleCount];
            int[] minTriangleTileY = new int[triangleCount];
            int[] maxTriangleTileY = new int[triangleCount];
            int[] tested = new int[triangleCount];

            int commandCount = upload.commands().size();
            int[] globalFirst = new int[commandCount];
            float[][] commandAabbs = new float[commandCount][];
            Arrays.fill(commandAabbs, new float[0]);
            int[] broadPhaseGeneration = new int[commandCount];
            byte[] broadPhaseResult = new byte[commandCount];
            Set<ZoneKey> coverage = new LinkedHashSet<>();

            int triangleIndex = 0;
            int minTileX = Integer.MAX_VALUE;
            int maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE;
            int maxTileY = Integer.MIN_VALUE;

            for (int commandIndex = 0; commandIndex < commandCount; commandIndex++) {
                GpuDrawCommand command = upload.commands().get(commandIndex);
                for (int offset = command.firstIndex();
                     offset + 2 < command.firstIndex() + command.indexCount(); offset += 3) {
                    int aIndex = upload.indexAt(offset);
                    int bIndex = upload.indexAt(offset + 1);
                    int cIndex = upload.indexAt(offset + 2);
                    GpuSceneVertex a = upload.vertices().get(aIndex);
                    GpuSceneVertex b = upload.vertices().get(bIndex);
                    GpuSceneVertex c = upload.vertices().get(cIndex);

                    int triMinX = floorTile(Math.min(a.x(), Math.min(b.x(), c.x())));
                    int triMaxX = floorTile(Math.max(a.x(), Math.max(b.x(), c.x())));
                    int triMinY = floorTile(Math.min(a.z(), Math.min(b.z(), c.z())));
                    int triMaxY = floorTile(Math.max(a.z(), Math.max(b.z(), c.z())));

                    localCommandIndices[triangleIndex] = commandIndex;
                    vertexA[triangleIndex] = aIndex;
                    vertexB[triangleIndex] = bIndex;
                    vertexC[triangleIndex] = cIndex;
                    faceOffsets[triangleIndex] = offset - command.firstIndex();
                    minTriangleTileX[triangleIndex] = triMinX;
                    maxTriangleTileX[triangleIndex] = triMaxX;
                    minTriangleTileY[triangleIndex] = triMinY;
                    maxTriangleTileY[triangleIndex] = triMaxY;

                    minTileX = Math.min(minTileX, triMinX);
                    maxTileX = Math.max(maxTileX, triMaxX);
                    minTileY = Math.min(minTileY, triMinY);
                    maxTileY = Math.max(maxTileY, triMaxY);

                    for (int zoneX = Math.floorDiv(triMinX, ZONE_TILES);
                         zoneX <= Math.floorDiv(triMaxX, ZONE_TILES); zoneX++) {
                        for (int zoneY = Math.floorDiv(triMinY, ZONE_TILES);
                             zoneY <= Math.floorDiv(triMaxY, ZONE_TILES); zoneY++) {
                            coverage.add(new ZoneKey(command.tile().plane(), zoneX, zoneY));
                        }
                    }
                    triangleIndex++;
                }
            }

            if (triangleCount == 0) {
                minTileX = 0;
                maxTileX = -1;
                minTileY = 0;
                maxTileY = -1;
            }

            return new SourceZone(upload,
                    localCommandIndices,
                    vertexA, vertexB, vertexC,
                    faceOffsets,
                    minTriangleTileX, maxTriangleTileX,
                    minTriangleTileY, maxTriangleTileY,
                    tested, globalFirst,
                    commandAabbs,
                    broadPhaseGeneration, broadPhaseResult,
                    Set.copyOf(coverage),
                    minTileX, maxTileX, minTileY, maxTileY);
        }

        void bindSnapshotOrdinal(int ordinal) {
            if (ordinal < 0) throw new IllegalArgumentException("Source ordinal cannot be negative");
            snapshotOrdinal = ordinal;
        }

        int snapshotOrdinal() { return snapshotOrdinal; }

        long handle(int triangleIndex) {
            return ((long) snapshotOrdinal << 32) | (triangleIndex & 0xFFFF_FFFFL);
        }

        GpuZoneUpload upload() { return upload; }
        Set<ZoneKey> coverage() { return coverage; }
        int triangleCount() { return localCommandIndices.length; }
        int minTileX() { return minTileX; }
        int maxTileX() { return maxTileX; }
        int minTileY() { return minTileY; }
        int maxTileY() { return maxTileY; }

        int localCommandIndex(int triangleIndex) {
            return localCommandIndices[triangleIndex];
        }

        GpuDrawCommand command(int triangleIndex) {
            return upload.commands().get(localCommandIndices[triangleIndex]);
        }

        GpuSceneVertex a(int triangleIndex) {
            return upload.vertices().get(vertexA[triangleIndex]);
        }

        GpuSceneVertex b(int triangleIndex) {
            return upload.vertices().get(vertexB[triangleIndex]);
        }

        GpuSceneVertex c(int triangleIndex) {
            return upload.vertices().get(vertexC[triangleIndex]);
        }

        int order(int triangleIndex) {
            return globalFirstIndices[localCommandIndices[triangleIndex]]
                    + faceOffsets[triangleIndex];
        }

        boolean overlaps(int triangleIndex, ZoneKey zone) {
            if (command(triangleIndex).tile().plane() != zone.plane()) return false;
            return maxTriangleTileX[triangleIndex] >= zone.minTileX()
                    && minTriangleTileX[triangleIndex] <= zone.maxTileX()
                    && maxTriangleTileY[triangleIndex] >= zone.minTileY()
                    && minTriangleTileY[triangleIndex] <= zone.maxTileY();
        }

        int minTriangleTileX(int triangleIndex) { return minTriangleTileX[triangleIndex]; }
        int maxTriangleTileX(int triangleIndex) { return maxTriangleTileX[triangleIndex]; }
        int minTriangleTileY(int triangleIndex) { return minTriangleTileY[triangleIndex]; }
        int maxTriangleTileY(int triangleIndex) { return maxTriangleTileY[triangleIndex]; }

        void beginBinding() {
            bindingCursor = 0;
        }

        void bindNext(GpuDrawCommand globalCommand) {
            if (bindingCursor >= upload.commands().size()) {
                throw new IllegalStateException("Too many global commands for picking source zone");
            }
            GpuDrawCommand local = upload.commands().get(bindingCursor);
            if (!sameCommand(local, globalCommand)) {
                throw new IllegalStateException("Picking command order diverged from zoned upload");
            }
            globalFirstIndices[bindingCursor] = globalCommand.firstIndex();
            commandAabbs[bindingCursor] = buildCommandAabbs(globalCommand);
            broadPhaseGeneration[bindingCursor] = 0;
            broadPhaseResult[bindingCursor] = 0;
            bindingCursor++;
        }

        void finishBinding() {
            if (bindingCursor != upload.commands().size()) {
                throw new IllegalStateException("Missing global commands for picking source zone");
            }
        }

        boolean markTested(int triangleIndex, int generation) {
            if (testedGeneration[triangleIndex] == generation) return false;
            testedGeneration[triangleIndex] = generation;
            return true;
        }

        /**
         * Returns 0 when this command has no client AABB, 1/2 for a cached
         * pass/reject, and 3/4 for a newly evaluated pass/reject.
         */
        byte broadPhase(int commandIndex, int generation,
                        float ox, float oy, float oz,
                        float dx, float dy, float dz,
                        float near, float far) {
            float[] bounds = commandAabbs[commandIndex];
            if (bounds.length == 0) return 0;

            if (broadPhaseGeneration[commandIndex] == generation) {
                return broadPhaseResult[commandIndex] == 1 ? (byte) 1 : (byte) 2;
            }

            boolean hit = false;
            for (int offset = 0; offset + 5 < bounds.length; offset += 6) {
                if (intersects(bounds, offset, ox, oy, oz, dx, dy, dz, near, far)) {
                    hit = true;
                    break;
                }
            }
            broadPhaseGeneration[commandIndex] = generation;
            broadPhaseResult[commandIndex] = (byte) (hit ? 1 : 2);
            return hit ? (byte) 3 : (byte) 4;
        }

        void clearTestedGenerations() {
            Arrays.fill(testedGeneration, 0);
            Arrays.fill(broadPhaseGeneration, 0);
            Arrays.fill(broadPhaseResult, (byte) 0);
        }

        private static float[] buildCommandAabbs(GpuDrawCommand command) {
            java.util.List<ClientModelBounds> bounds = command.clientRenderableBounds();
            if (bounds.isEmpty()) return new float[0];

            java.util.List<ClientRenderablePlacement> placements =
                    command.clientRenderablePlacements();
            if (placements.size() != bounds.size()) {
                throw new IllegalStateException("Client bounds and placements must stay aligned");
            }

            GameObjectSceneMetadata scene = command.gameObjectSceneMetadata();
            float centerX = scene.present() ? scene.sizeX() * 64.0f : 64.0f;
            float centerZ = scene.present() ? scene.sizeY() * 64.0f : 64.0f;
            float anchorX = command.modelAnchorX() * 128.0f + centerX;
            float anchorY = command.placementHeight();
            float anchorZ = command.modelAnchorY() * 128.0f + centerZ;

            float[] result = new float[bounds.size() * 6];
            for (int index = 0; index < bounds.size(); index++) {
                ClientModelBounds.Aabb local = bounds.get(index).drawAabb();
                ClientRenderablePlacement placement = placements.get(index);
                float tx = anchorX + placement.offsetX();
                float tz = anchorZ + placement.offsetZ();
                int offset = index * 6;
                result[offset] = tx + local.minX();
                result[offset + 1] = tx + local.maxX();
                result[offset + 2] = anchorY + local.minY();
                result[offset + 3] = anchorY + local.maxY();
                result[offset + 4] = tz + local.minZ();
                result[offset + 5] = tz + local.maxZ();
            }
            return result;
        }

        private static boolean intersects(
                float[] bounds, int offset,
                float ox, float oy, float oz,
                float dx, float dy, float dz,
                float near, float far) {
            float enter = near;
            float exit = far;

            float minX = bounds[offset];
            float maxX = bounds[offset + 1];
            float minY = bounds[offset + 2];
            float maxY = bounds[offset + 3];
            float minZ = bounds[offset + 4];
            float maxZ = bounds[offset + 5];

            if (Math.abs(dx) <= 1.0e-5f) {
                if (ox < minX || ox > maxX) return false;
            } else {
                float a = (minX - ox) / dx;
                float b = (maxX - ox) / dx;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (exit < enter) return false;
            }

            if (Math.abs(dy) <= 1.0e-5f) {
                if (oy < minY || oy > maxY) return false;
            } else {
                float a = (minY - oy) / dy;
                float b = (maxY - oy) / dy;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (exit < enter) return false;
            }

            if (Math.abs(dz) <= 1.0e-5f) {
                return oz >= minZ && oz <= maxZ;
            }
            float a = (minZ - oz) / dz;
            float b = (maxZ - oz) / dz;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            return exit >= enter;
        }

        private static boolean sameCommand(GpuDrawCommand local, GpuDrawCommand global) {
            return local.tile().equals(global.tile())
                    && local.scenePlane() == global.scenePlane()
                    && local.planeCullLevel() == global.planeCullLevel()
                    && local.layer() == global.layer()
                    && local.pass() == global.pass()
                    && local.indexCount() == global.indexCount()
                    && local.textureId() == global.textureId()
                    && local.priority() == global.priority()
                    && local.depthBias() == global.depthBias()
                    && local.objectId() == global.objectId()
                    && local.renderMode() == global.renderMode()
                    && local.wallDecorationPresentation().equals(global.wallDecorationPresentation());
        }
    }

    private static final class PickingZone {
        private static final long[] EMPTY_BUCKET = new long[0];

        private final ZoneKey coordinate;
        private final long[][] buckets;

        private PickingZone(ZoneKey coordinate, long[][] buckets) {
            this.coordinate = coordinate;
            this.buckets = buckets;
        }

        static PickingZone build(ZoneKey coordinate, Collection<SourceZone> sources) {
            LongBucketBuilder[] mutable = new LongBucketBuilder[ZONE_TILES * ZONE_TILES];

            for (SourceZone source : sources) {
                if (!source.coverage().contains(coordinate)) continue;
                for (int triangleIndex = 0;
                     triangleIndex < source.triangleCount(); triangleIndex++) {
                    if (!source.overlaps(triangleIndex, coordinate)) continue;
                    int minX = Math.max(source.minTriangleTileX(triangleIndex),
                            coordinate.minTileX());
                    int maxX = Math.min(source.maxTriangleTileX(triangleIndex),
                            coordinate.maxTileX());
                    int minY = Math.max(source.minTriangleTileY(triangleIndex),
                            coordinate.minTileY());
                    int maxY = Math.min(source.maxTriangleTileY(triangleIndex),
                            coordinate.maxTileY());
                    long handle = source.handle(triangleIndex);
                    for (int tileX = minX; tileX <= maxX; tileX++) {
                        for (int tileY = minY; tileY <= maxY; tileY++) {
                            int cell = cellIndex(tileX, tileY);
                            LongBucketBuilder bucket = mutable[cell];
                            if (bucket == null) mutable[cell] = bucket = new LongBucketBuilder();
                            bucket.add(handle);
                        }
                    }
                }
            }

            long[][] buckets = new long[ZONE_TILES * ZONE_TILES][];
            for (int index = 0; index < buckets.length; index++) {
                LongBucketBuilder bucket = mutable[index];
                buckets[index] = bucket == null ? EMPTY_BUCKET : bucket.toArray();
            }
            return new PickingZone(coordinate, buckets);
        }

        long[] bucket(int tileX, int tileY) {
            if (tileX < coordinate.minTileX() || tileX > coordinate.maxTileX()
                    || tileY < coordinate.minTileY() || tileY > coordinate.maxTileY()) {
                return EMPTY_BUCKET;
            }
            return buckets[cellIndex(tileX, tileY)];
        }

        private static int cellIndex(int tileX, int tileY) {
            int localX = Math.floorMod(tileX, ZONE_TILES);
            int localY = Math.floorMod(tileY, ZONE_TILES);
            return localY * ZONE_TILES + localX;
        }
    }

    private static final class LongBucketBuilder {
        private long[] values = new long[8];
        private int size;

        void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length << 1);
            }
            values[size++] = value;
        }

        long[] toArray() {
            return size == values.length ? values : Arrays.copyOf(values, size);
        }
    }

    private record ZoneKey(int plane, int zoneX, int zoneY) implements Comparable<ZoneKey> {
        private ZoneKey {
            if (plane < 0 || plane >= PLANE_COUNT) {
                throw new IllegalArgumentException("Picking zone plane must be 0..3");
            }
        }

        int minTileX() { return zoneX * ZONE_TILES; }
        int maxTileX() { return minTileX() + ZONE_TILES - 1; }
        int minTileY() { return zoneY * ZONE_TILES; }
        int maxTileY() { return minTileY() + ZONE_TILES - 1; }

        @Override
        public int compareTo(ZoneKey other) {
            int byPlane = Integer.compare(plane, other.plane);
            if (byPlane != 0) return byPlane;
            int byX = Integer.compare(zoneX, other.zoneX);
            return byX != 0 ? byX : Integer.compare(zoneY, other.zoneY);
        }
    }

    private static int floorTile(float worldCoordinate) {
        return (int) Math.floor(worldCoordinate / 128.0f);
    }
}
