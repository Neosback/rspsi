package com.rspsi.editor.debug;

import com.rspsi.editor.model.WorldWindow;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, frontend-neutral result of building the enabled debug views. */
public record DebugOverlaySnapshot(
        DebugOverlaySettings settings,
        WorldWindow worldWindow,
        List<DebugGridLine> gridLines,
        Map<Integer, List<DebugTileSnapshot>> tilesByPlane
) {
    public DebugOverlaySnapshot {
        settings = Objects.requireNonNull(settings, "settings");
        worldWindow = Objects.requireNonNull(worldWindow, "worldWindow");
        gridLines = List.copyOf(gridLines == null ? List.of() : gridLines);
        tilesByPlane = Map.copyOf(tilesByPlane == null ? Map.of() : tilesByPlane);
    }

    public List<DebugTileSnapshot> tiles(int plane) {
        return tilesByPlane.getOrDefault(plane, List.of());
    }
}
