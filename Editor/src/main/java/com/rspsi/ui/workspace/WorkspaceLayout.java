package com.rspsi.ui.workspace;

import java.util.List;
import java.util.Objects;

/** Versioned per-workspace UI layout, never persisted in a Studio project. */
public record WorkspaceLayout(String workspaceId, int version, List<PanelLayoutState> panels) {
    public static final int CURRENT_VERSION = 1;

    public WorkspaceLayout {
        workspaceId = Objects.requireNonNull(workspaceId, "workspaceId").trim();
        if (workspaceId.isEmpty() || version < 1) {
            throw new IllegalArgumentException("Invalid workspace layout");
        }
        panels = List.copyOf(panels == null ? List.of() : panels);
    }
}
