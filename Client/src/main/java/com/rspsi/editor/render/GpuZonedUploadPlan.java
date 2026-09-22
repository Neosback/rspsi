package com.rspsi.editor.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native-facing geometry partition of one flat upload plan.
 *
 * <p>Geometry is resident by absolute 8x8 world zone, while commandRefs retain
 * the flat plan's exact global command order for priority/alpha semantics.</p>
 */
public record GpuZonedUploadPlan(
        Map<WorldZoneCoordinate, GpuZoneUpload> zones,
        List<GpuZonedDrawCommand> commandRefs,
        String sourceFingerprint
) {
    public GpuZonedUploadPlan {
        zones = Map.copyOf(Objects.requireNonNull(zones, "zones"));
        commandRefs = List.copyOf(Objects.requireNonNull(commandRefs, "commandRefs"));
        sourceFingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint").trim();
        if (sourceFingerprint.isEmpty()) {
            throw new IllegalArgumentException("Zoned upload source fingerprint cannot be empty");
        }
        for (GpuZonedDrawCommand ref : commandRefs) {
            GpuZoneUpload zone = zones.get(ref.zone());
            if (zone == null) throw new IllegalArgumentException("Command references a missing zone");
            if (ref.localFirstIndex() + ref.command().indexCount() > zone.indices().size()) {
                throw new IllegalArgumentException("Command exceeds its zone-local index buffer");
            }
        }
    }

    public int vertexCount() {
        return zones.values().stream().mapToInt(zone -> zone.vertices().size()).sum();
    }

    public int indexCount() {
        return zones.values().stream().mapToInt(zone -> zone.indices().size()).sum();
    }
}
