package com.rspsi.editor.render;

import java.util.Objects;

/** One globally ordered draw command pointing at a zone-local index range. */
public record GpuZonedDrawCommand(
        GpuDrawCommand command,
        WorldZoneCoordinate zone,
        int localFirstIndex
) {
    public GpuZonedDrawCommand {
        command = Objects.requireNonNull(command, "command");
        zone = Objects.requireNonNull(zone, "zone");
        if (!zone.contains(command.tile())) {
            throw new IllegalArgumentException("Zoned command does not belong to its zone");
        }
        if (localFirstIndex < 0) {
            throw new IllegalArgumentException("Zone-local first index cannot be negative");
        }
    }
}
