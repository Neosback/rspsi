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
        int expectedIndices = 0;
        for (int commandIndex : commandIndices) {
            expectedIndices += plan.commands().get(commandIndex).indexCount();
        }
        MutableZone target = new MutableZone(zone, expectedIndices);
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
        private final IntIntMap globalToLocal;

        private MutableZone(WorldZoneCoordinate zone, int expectedIndices) {
            this.zone = zone;
            this.globalToLocal = new IntIntMap(expectedIndices);
        }

        private int append(GpuUploadPlan plan, int commandIndex, GpuDrawCommand command) {
            int localFirst = indices.size();
            for (int offset = 0; offset < command.indexCount(); offset++) {
                int globalIndex = plan.indexedVertexIndex(commandIndex, offset);
                int localIndex = globalToLocal.get(globalIndex);
                if (localIndex < 0) {
                    localIndex = vertices.size();
                    vertices.add(plan.directVertexAt(globalIndex));
                    globalToLocal.put(globalIndex, localIndex);
                }
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
                    GpuZoneUpload.fingerprints(vertices, indices, commands));
        }
    }

    /** Open-addressing map of non-negative int keys to non-negative values; -1 when absent. */
    private static final class IntIntMap {
        private int[] keys;
        private int[] values;
        private int size;

        IntIntMap(int expected) {
            int capacity = Integer.highestOneBit(Math.max(128, expected * 2 - 1)) << 1;
            keys = new int[capacity];
            values = new int[capacity];
            java.util.Arrays.fill(keys, -1);
        }

        int get(int key) {
            int mask = keys.length - 1;
            for (int slot = mix(key) & mask; ; slot = (slot + 1) & mask) {
                int current = keys[slot];
                if (current == key) return values[slot];
                if (current == -1) return -1;
            }
        }

        void put(int key, int value) {
            if ((size + 1) * 2 > keys.length) grow();
            int mask = keys.length - 1;
            int slot = mix(key) & mask;
            while (keys[slot] != -1 && keys[slot] != key) slot = (slot + 1) & mask;
            if (keys[slot] == -1) size++;
            keys[slot] = key;
            values[slot] = value;
        }

        private void grow() {
            int[] oldKeys = keys;
            int[] oldValues = values;
            keys = new int[oldKeys.length * 2];
            values = new int[oldKeys.length * 2];
            java.util.Arrays.fill(keys, -1);
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != -1) put(oldKeys[i], oldValues[i]);
            }
        }

        private static int mix(int key) {
            int h = key * 0x9E3779B9;
            return h ^ (h >>> 16);
        }
    }
}
