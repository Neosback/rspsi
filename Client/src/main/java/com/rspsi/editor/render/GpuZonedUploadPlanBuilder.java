package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Partitions a flat upload plan into native-ready absolute 8x8 zone buffers. */
public final class GpuZonedUploadPlanBuilder {
    public GpuZonedUploadPlan build(GpuUploadPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<WorldZoneCoordinate, MutableZone> working = new LinkedHashMap<>();
        List<GpuZonedDrawCommand> refs = new ArrayList<>(plan.commands().size());

        for (GpuDrawCommand command : plan.commands()) {
            WorldZoneCoordinate zone = WorldZoneCoordinate.from(command.tile());
            MutableZone target = working.computeIfAbsent(zone, ignored -> new MutableZone(zone));
            int localFirst = target.append(plan, command);
            refs.add(new GpuZonedDrawCommand(command, zone, localFirst));
        }

        Map<WorldZoneCoordinate, GpuZoneUpload> zones = new LinkedHashMap<>();
        working.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> zones.put(entry.getKey(), entry.getValue().freeze()));
        return new GpuZonedUploadPlan(zones, refs, plan.fingerprint());
    }

    private static final class MutableZone {
        private final WorldZoneCoordinate zone;
        private final List<GpuSceneVertex> vertices = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();
        private final List<GpuDrawCommand> commands = new ArrayList<>();
        private final Map<Integer, Integer> globalToLocal = new LinkedHashMap<>();

        private MutableZone(WorldZoneCoordinate zone) {
            this.zone = zone;
        }

        private int append(GpuUploadPlan plan, GpuDrawCommand command) {
            int localFirst = indices.size();
            for (int offset = 0; offset < command.indexCount(); offset++) {
                int globalIndex = plan.indices().get(command.firstIndex() + offset);
                int localIndex = globalToLocal.computeIfAbsent(globalIndex, ignored -> {
                    int next = vertices.size();
                    vertices.add(plan.vertices().get(globalIndex));
                    return next;
                });
                indices.add(localIndex);
            }
            commands.add(new GpuDrawCommand(
                    command.tile(), command.scenePlane(), command.planeCullLevel(),
                    command.layer(), command.pass(), localFirst, command.indexCount(),
                    command.textureId(), command.priority(), command.depthBias(),
                    command.objectId(), command.renderMode(), command.wallDecorationPresentation()));
            return localFirst;
        }

        private GpuZoneUpload freeze() {
            return new GpuZoneUpload(zone, vertices, indices, commands,
                    GpuZoneUpload.fingerprint(vertices, indices));
        }
    }
}
