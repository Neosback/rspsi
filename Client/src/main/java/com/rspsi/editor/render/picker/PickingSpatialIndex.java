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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Incremental CPU spatial index for scene picking.
 *
 * <p>The renderer already retains immutable geometry by 8x8 world zone. This
 * class follows that ownership boundary: unchanged {@link GpuZoneUpload}
 * instances retain their decoded triangle metadata, while picking zones are
 * rebuilt only when a changed source zone contributes geometry to them.</p>
 *
 * <p>Picking zones are spatial rather than ownership zones. A large loc
 * anchored at the edge of one GPU zone can contain triangles extending into a
 * neighboring zone. Those triangles are inserted into every spatial zone/tile
 * overlapped by their world-space bounds so incremental reuse preserves the
 * existing DDA pick behavior.</p>
 */
final class PickingSpatialIndex {
    static final int ZONE_TILES = 8;
    static final int PLANE_COUNT = 4;

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

        bindGlobalOrder(zonedPlan, nextSources);

        Set<ZoneKey> requiredSpatialZones = new TreeSet<>();
        for (SourceZone source : nextSources.values()) {
            requiredSpatialZones.addAll(source.coverage());
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
        snapshot = Snapshot.build(zones, sources.values());
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
        private static final TriangleRef[] EMPTY_BUCKET = new TriangleRef[0];
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
                              Collection<SourceZone> sources) {
            if (zones.isEmpty() || sources.isEmpty()) return empty();

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

            int minTileX = Integer.MAX_VALUE;
            int maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE;
            int maxTileY = Integer.MIN_VALUE;
            int triangles = 0;
            SourceZone[] sourceArray = sources.toArray(new SourceZone[0]);
            for (SourceZone source : sourceArray) {
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

        TriangleRef[] bucket(int plane, int tileX, int tileY) {
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

        int minTileX() { return minTileX; }
        int maxTileX() { return maxTileX; }
        int minTileY() { return minTileY; }
        int maxTileY() { return maxTileY; }
        int triangleCount() { return triangleCount; }

        boolean isEmpty() {
            return triangleCount == 0;
        }

        void clearTestedGenerations() {
            for (SourceZone source : sources) source.clearTestedGenerations();
        }

        private static int denseIndex(int plane, int zoneX, int zoneY,
                                      int minZoneX, int minZoneY,
                                      int width, int height) {
            int localX = zoneX - minZoneX;
            int localY = zoneY - minZoneY;
            return (plane * height + localY) * width + localX;
        }
    }

    static final class TriangleRef {
        private final SourceZone source;
        private final int sourceTriangleIndex;
        private final int localCommandIndex;
        private final GpuDrawCommand command;
        private final GpuSceneVertex a;
        private final GpuSceneVertex b;
        private final GpuSceneVertex c;
        private final int faceOffset;
        private final int minTileX;
        private final int maxTileX;
        private final int minTileY;
        private final int maxTileY;

        private TriangleRef(SourceZone source, int sourceTriangleIndex, int localCommandIndex,
                            GpuDrawCommand command,
                            GpuSceneVertex a, GpuSceneVertex b, GpuSceneVertex c,
                            int faceOffset) {
            this.source = source;
            this.sourceTriangleIndex = sourceTriangleIndex;
            this.localCommandIndex = localCommandIndex;
            this.command = command;
            this.a = a;
            this.b = b;
            this.c = c;
            this.faceOffset = faceOffset;
            this.minTileX = floorTile(Math.min(a.x(), Math.min(b.x(), c.x())));
            this.maxTileX = floorTile(Math.max(a.x(), Math.max(b.x(), c.x())));
            this.minTileY = floorTile(Math.min(a.z(), Math.min(b.z(), c.z())));
            this.maxTileY = floorTile(Math.max(a.z(), Math.max(b.z(), c.z())));
        }

        GpuDrawCommand command() { return command; }
        GpuSceneVertex a() { return a; }
        GpuSceneVertex b() { return b; }
        GpuSceneVertex c() { return c; }

        int order() {
            return source.globalFirstIndex(localCommandIndex) + faceOffset;
        }

        boolean markTested(int generation) {
            return source.markTested(sourceTriangleIndex, generation);
        }

        byte broadPhase(int generation,
                        float ox, float oy, float oz,
                        float dx, float dy, float dz,
                        float near, float far) {
            return source.broadPhase(localCommandIndex, generation,
                    ox, oy, oz, dx, dy, dz, near, far);
        }

        boolean overlaps(ZoneKey zone) {
            if (command.tile().plane() != zone.plane()) return false;
            return maxTileX >= zone.minTileX() && minTileX <= zone.maxTileX()
                    && maxTileY >= zone.minTileY() && minTileY <= zone.maxTileY();
        }
    }

    private static final class SourceZone {
        private final GpuZoneUpload upload;
        private final TriangleRef[] triangles;
        private final int[] testedGeneration;
        private final int[] globalFirstIndices;
        private final WorldAabb[][] commandAabbs;
        private final int[] broadPhaseGeneration;
        private final byte[] broadPhaseResult;
        private final Set<ZoneKey> coverage;
        private final int minTileX;
        private final int maxTileX;
        private final int minTileY;
        private final int maxTileY;
        private int bindingCursor;

        private SourceZone(GpuZoneUpload upload, TriangleRef[] triangles,
                           int[] testedGeneration, int[] globalFirstIndices,
                           WorldAabb[][] commandAabbs,
                           int[] broadPhaseGeneration, byte[] broadPhaseResult,
                           Set<ZoneKey> coverage,
                           int minTileX, int maxTileX, int minTileY, int maxTileY) {
            this.upload = upload;
            this.triangles = triangles;
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

            TriangleRef[] refs = new TriangleRef[triangleCount];
            int[] tested = new int[triangleCount];
            int[] globalFirst = new int[upload.commands().size()];
            WorldAabb[][] commandAabbs = new WorldAabb[upload.commands().size()][];
            Arrays.fill(commandAabbs, new WorldAabb[0]);
            int[] broadPhaseGeneration = new int[upload.commands().size()];
            byte[] broadPhaseResult = new byte[upload.commands().size()];
            Set<ZoneKey> coverage = new LinkedHashSet<>();
            SourceZone shell = new SourceZone(upload, refs, tested, globalFirst,
                    commandAabbs, broadPhaseGeneration, broadPhaseResult, coverage,
                    Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);

            int triangleIndex = 0;
            int minTileX = Integer.MAX_VALUE;
            int maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE;
            int maxTileY = Integer.MIN_VALUE;
            for (int commandIndex = 0; commandIndex < upload.commands().size(); commandIndex++) {
                GpuDrawCommand command = upload.commands().get(commandIndex);
                for (int offset = command.firstIndex();
                     offset + 2 < command.firstIndex() + command.indexCount(); offset += 3) {
                    GpuSceneVertex a = upload.vertices().get(upload.indices().get(offset));
                    GpuSceneVertex b = upload.vertices().get(upload.indices().get(offset + 1));
                    GpuSceneVertex c = upload.vertices().get(upload.indices().get(offset + 2));
                    TriangleRef ref = new TriangleRef(shell, triangleIndex, commandIndex,
                            command, a, b, c, offset - command.firstIndex());
                    refs[triangleIndex++] = ref;

                    minTileX = Math.min(minTileX, ref.minTileX);
                    maxTileX = Math.max(maxTileX, ref.maxTileX);
                    minTileY = Math.min(minTileY, ref.minTileY);
                    maxTileY = Math.max(maxTileY, ref.maxTileY);
                    for (int zoneX = Math.floorDiv(ref.minTileX, ZONE_TILES);
                         zoneX <= Math.floorDiv(ref.maxTileX, ZONE_TILES); zoneX++) {
                        for (int zoneY = Math.floorDiv(ref.minTileY, ZONE_TILES);
                             zoneY <= Math.floorDiv(ref.maxTileY, ZONE_TILES); zoneY++) {
                            coverage.add(new ZoneKey(command.tile().plane(), zoneX, zoneY));
                        }
                    }
                }
            }

            if (triangleCount == 0) {
                minTileX = 0;
                maxTileX = -1;
                minTileY = 0;
                maxTileY = -1;
            }

            SourceZone result = new SourceZone(upload, refs, tested, globalFirst,
                    commandAabbs, broadPhaseGeneration, broadPhaseResult,
                    Set.copyOf(coverage), minTileX, maxTileX, minTileY, maxTileY);
            for (int index = 0; index < refs.length; index++) {
                TriangleRef old = refs[index];
                refs[index] = new TriangleRef(result, old.sourceTriangleIndex, old.localCommandIndex,
                        old.command, old.a, old.b, old.c, old.faceOffset);
            }
            return result;
        }

        GpuZoneUpload upload() { return upload; }
        Set<ZoneKey> coverage() { return coverage; }
        int triangleCount() { return triangles.length; }
        int minTileX() { return minTileX; }
        int maxTileX() { return maxTileX; }
        int minTileY() { return minTileY; }
        int maxTileY() { return maxTileY; }

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
            // GpuZoneUpload intentionally carries only render-critical command
            // metadata. Bind the complete current-plan command here so client
            // model bounds, placements and model anchors stay available to the
            // picking broad phase without making zone geometry non-reusable.
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

        int globalFirstIndex(int localCommandIndex) {
            return globalFirstIndices[localCommandIndex];
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
            WorldAabb[] bounds = commandAabbs[commandIndex];
            if (bounds.length == 0) return 0;

            if (broadPhaseGeneration[commandIndex] == generation) {
                return broadPhaseResult[commandIndex] == 1 ? (byte) 1 : (byte) 2;
            }

            boolean hit = false;
            for (WorldAabb bound : bounds) {
                if (bound.intersects(ox, oy, oz, dx, dy, dz, near, far)) {
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

        private static WorldAabb[] buildCommandAabbs(GpuDrawCommand command) {
            List<ClientModelBounds> bounds = command.clientRenderableBounds();
            if (bounds.isEmpty()) return new WorldAabb[0];

            List<ClientRenderablePlacement> placements = command.clientRenderablePlacements();
            if (placements.size() != bounds.size()) {
                throw new IllegalStateException("Client bounds and placements must stay aligned");
            }

            GameObjectSceneMetadata scene = command.gameObjectSceneMetadata();
            float centerX = scene.present() ? scene.sizeX() * 64.0f : 64.0f;
            float centerZ = scene.present() ? scene.sizeY() * 64.0f : 64.0f;
            float anchorX = command.modelAnchorX() * 128.0f + centerX;
            float anchorY = command.placementHeight();
            float anchorZ = command.modelAnchorY() * 128.0f + centerZ;

            WorldAabb[] result = new WorldAabb[bounds.size()];
            for (int index = 0; index < bounds.size(); index++) {
                ClientModelBounds.Aabb local = bounds.get(index).drawAabb();
                ClientRenderablePlacement placement = placements.get(index);
                float tx = anchorX + placement.offsetX();
                float tz = anchorZ + placement.offsetZ();
                result[index] = new WorldAabb(
                        tx + local.minX(), tx + local.maxX(),
                        anchorY + local.minY(), anchorY + local.maxY(),
                        tz + local.minZ(), tz + local.maxZ());
            }
            return result;
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

    private record WorldAabb(float minX, float maxX,
                             float minY, float maxY,
                             float minZ, float maxZ) {
        private boolean intersects(float ox, float oy, float oz,
                                   float dx, float dy, float dz,
                                   float near, float far) {
            float enter = near;
            float exit = far;

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
    }

    private static final class PickingZone {
        private static final TriangleRef[] EMPTY_BUCKET = new TriangleRef[0];

        private final ZoneKey coordinate;
        private final TriangleRef[][] buckets;

        private PickingZone(ZoneKey coordinate, TriangleRef[][] buckets) {
            this.coordinate = coordinate;
            this.buckets = buckets;
        }

        static PickingZone build(ZoneKey coordinate, Collection<SourceZone> sources) {
            @SuppressWarnings("unchecked")
            List<TriangleRef>[] mutable = new List[ZONE_TILES * ZONE_TILES];

            for (SourceZone source : sources) {
                if (!source.coverage().contains(coordinate)) continue;
                for (TriangleRef triangle : source.triangles) {
                    if (!triangle.overlaps(coordinate)) continue;
                    int minX = Math.max(triangle.minTileX, coordinate.minTileX());
                    int maxX = Math.min(triangle.maxTileX, coordinate.maxTileX());
                    int minY = Math.max(triangle.minTileY, coordinate.minTileY());
                    int maxY = Math.min(triangle.maxTileY, coordinate.maxTileY());
                    for (int tileX = minX; tileX <= maxX; tileX++) {
                        for (int tileY = minY; tileY <= maxY; tileY++) {
                            int cell = cellIndex(tileX, tileY);
                            List<TriangleRef> bucket = mutable[cell];
                            if (bucket == null) mutable[cell] = bucket = new ArrayList<>();
                            bucket.add(triangle);
                        }
                    }
                }
            }

            TriangleRef[][] buckets = new TriangleRef[ZONE_TILES * ZONE_TILES][];
            for (int index = 0; index < buckets.length; index++) {
                List<TriangleRef> bucket = mutable[index];
                buckets[index] = bucket == null ? EMPTY_BUCKET : bucket.toArray(new TriangleRef[0]);
            }
            return new PickingZone(coordinate, buckets);
        }

        TriangleRef[] bucket(int tileX, int tileY) {
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
