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
        Map<WorldZoneCoordinate, List<Integer>> grouped = commandIndicesByZone(plan);
        Map<WorldZoneCoordinate, GpuZoneUpload> zones = new LinkedHashMap<>();
        grouped.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> zones.put(entry.getKey(),
                        buildZone(plan, entry.getKey(), entry.getValue())));
        return assemble(plan, zones);
    }

    static Map<WorldZoneCoordinate, List<Integer>> commandIndicesByZone(GpuUploadPlan plan) {
        Map<WorldZoneCoordinate, List<Integer>> grouped = new LinkedHashMap<>();
        List<GpuDrawCommand> commands = plan.commands();
        for (int index = 0; index < commands.size(); index++) {
            WorldZoneCoordinate zone = WorldZoneCoordinate.from(commands.get(index).tile());
            grouped.computeIfAbsent(zone, ignored -> new ArrayList<>()).add(index);
        }
        return grouped;
    }

    static GpuZoneUpload buildZone(GpuUploadPlan plan, WorldZoneCoordinate zone,
                                   List<Integer> commandIndices) {
        MutableZone target = new MutableZone(zone);
        for (int commandIndex : commandIndices) {
            target.append(plan, commandIndex, plan.commands().get(commandIndex));
        }
        return target.freeze();
    }

    static GpuZonedUploadPlan assemble(GpuUploadPlan plan,
                                       Map<WorldZoneCoordinate, GpuZoneUpload> zones) {
        Map<WorldZoneCoordinate, Integer> cursor = new java.util.HashMap<>();
        List<GpuZonedDrawCommand> refs = new ArrayList<>(plan.commands().size());
        for (GpuDrawCommand command : plan.commands()) {
            WorldZoneCoordinate zone = WorldZoneCoordinate.from(command.tile());
            GpuZoneUpload upload = zones.get(zone);
            if (upload == null) throw new IllegalStateException("Missing upload for zone " + zone);
            int commandOffset = cursor.getOrDefault(zone, 0);
            if (commandOffset >= upload.commands().size()) {
                throw new IllegalStateException("Zone command mapping is shorter than flat plan");
            }
            GpuDrawCommand local = upload.commands().get(commandOffset);
            if (!sameCommandMetadata(command, local)) {
                throw new IllegalStateException("Zone command metadata diverged from flat plan");
            }
            refs.add(new GpuZonedDrawCommand(command, zone, local.firstIndex()));
            cursor.put(zone, commandOffset + 1);
        }
        return new GpuZonedUploadPlan(zones, refs, plan.fingerprint());
    }

    static boolean compatibleCommands(GpuUploadPlan plan, List<Integer> commandIndices,
                                      GpuZoneUpload cached) {
        if (commandIndices.size() != cached.commands().size()) return false;
        for (int index = 0; index < commandIndices.size(); index++) {
            if (!sameCommandMetadata(plan.commands().get(commandIndices.get(index)),
                    cached.commands().get(index))) {
                return false;
            }
        }
        return true;
    }

    static boolean sameCommandMetadata(GpuDrawCommand first, GpuDrawCommand second) {
        return first.tile().equals(second.tile())
                && first.scenePlane() == second.scenePlane()
                && first.planeCullLevel() == second.planeCullLevel()
                && first.layer() == second.layer()
                && first.pass() == second.pass()
                && first.indexCount() == second.indexCount()
                && first.textureId() == second.textureId()
                && first.priority() == second.priority()
                && first.depthBias() == second.depthBias()
                && first.objectId() == second.objectId()
                && first.renderMode() == second.renderMode()
                && first.wallDecorationPresentation().equals(second.wallDecorationPresentation());
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

        private int append(GpuUploadPlan plan, int commandIndex, GpuDrawCommand command) {
            int localFirst = indices.size();
            for (int offset = 0; offset < command.indexCount(); offset++) {
                int globalIndex = plan.indexedVertexIndex(commandIndex, offset);
                int currentOffset = offset;
                int localIndex = globalToLocal.computeIfAbsent(globalIndex, ignored -> {
                    int next = vertices.size();
                    vertices.add(plan.indexedVertex(commandIndex, currentOffset));
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
