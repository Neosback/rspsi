package com.rspsi.ui.workspace;

import com.rspsi.editor.ui.DockRegion;

import java.util.Objects;

/** UI-owned placement state; it is intentionally outside project/world data. */
public record PanelLayoutState(
        String panelId,
        DockRegion region,
        boolean visible,
        int tabOrder,
        double size,
        boolean detached,
        WindowBounds detachedBounds
) {
    public PanelLayoutState {
        panelId = Objects.requireNonNull(panelId, "panelId").trim();
        region = Objects.requireNonNull(region, "region");
        if (panelId.isEmpty() || tabOrder < 0 || !Double.isFinite(size) || size < 0) {
            throw new IllegalArgumentException("Invalid panel layout state");
        }
        if (!detached && detachedBounds != null) {
            throw new IllegalArgumentException("Docked panels cannot have detached bounds");
        }
    }
}
