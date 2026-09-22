package com.rspsi.editor.render;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reuses immutable native zone uploads when their geometry/command contract is
 * unchanged, rebuilding only dirty, missing, or structurally mismatched zones.
 */
public final class IncrementalGpuZonedUploadPlanBuilder {
    private final Map<WorldZoneCoordinate, GpuZoneUpload> cache = new LinkedHashMap<>();
    private int lastRebuiltZoneCount;
    private int lastReusedZoneCount;

    public GpuZonedUploadPlan buildInitial(GpuUploadPlan plan) {
        cache.clear();
        return build(plan, Set.of());
    }

    public GpuZonedUploadPlan build(GpuUploadPlan plan, Set<WorldZoneCoordinate> dirtyZones) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(dirtyZones, "dirtyZones");

        Map<WorldZoneCoordinate, List<Integer>> grouped =
                GpuZonedUploadPlanBuilder.commandIndicesByZone(plan);
        Map<WorldZoneCoordinate, GpuZoneUpload> next = new LinkedHashMap<>();
        lastRebuiltZoneCount = 0;
        lastReusedZoneCount = 0;

        for (Map.Entry<WorldZoneCoordinate, List<Integer>> entry : grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            WorldZoneCoordinate zone = entry.getKey();
            GpuZoneUpload cached = cache.get(zone);
            if (cached != null
                    && !dirtyZones.contains(zone)
                    && GpuZonedUploadPlanBuilder.compatibleCommands(
                            plan, entry.getValue(), cached)) {
                next.put(zone, cached);
                lastReusedZoneCount++;
            } else {
                next.put(zone, GpuZonedUploadPlanBuilder.buildZone(
                        plan, zone, entry.getValue()));
                lastRebuiltZoneCount++;
            }
        }

        cache.clear();
        cache.putAll(next);
        return GpuZonedUploadPlanBuilder.assemble(plan, next);
    }

    public void invalidateAll() {
        cache.clear();
        lastRebuiltZoneCount = 0;
        lastReusedZoneCount = 0;
    }

    public IncrementalGpuZonedUploadPlanBuilder fork() {
        IncrementalGpuZonedUploadPlanBuilder copy =
                new IncrementalGpuZonedUploadPlanBuilder();
        copy.cache.putAll(cache);
        copy.lastRebuiltZoneCount = lastRebuiltZoneCount;
        copy.lastReusedZoneCount = lastReusedZoneCount;
        return copy;
    }

    public int cachedZoneCount() {
        return cache.size();
    }

    public int lastRebuiltZoneCount() {
        return lastRebuiltZoneCount;
    }

    public int lastReusedZoneCount() {
        return lastReusedZoneCount;
    }
}
