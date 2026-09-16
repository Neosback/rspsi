package com.rspsi.editor.ui;

import java.util.Objects;

/** Placement of a known panel within a workspace. */
public record PanelPlacement(String panelId, DockRegion region, int order) {
    public PanelPlacement {
        panelId = Objects.requireNonNull(panelId, "panelId").trim();
        if (panelId.isEmpty()) {
            throw new IllegalArgumentException("Panel id cannot be empty");
        }
        region = Objects.requireNonNull(region, "region");
        if (order < 0) {
            throw new IllegalArgumentException("Panel order cannot be negative");
        }
    }
}
