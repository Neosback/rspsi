package com.rspsi.editor.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A constrained, named editor layout that can be rendered by any frontend. */
public record WorkspaceDefinition(String id, List<PanelPlacement> placements) {
    public WorkspaceDefinition {
        id = Objects.requireNonNull(id, "id").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Workspace id cannot be empty");
        }
        placements = List.copyOf(placements == null ? List.of() : placements);
        Set<String> panelIds = new HashSet<>();
        for (PanelPlacement placement : placements) {
            if (!panelIds.add(placement.panelId())) {
                throw new IllegalArgumentException("Workspace contains duplicate panel: " + placement.panelId());
            }
        }
    }
}
