package com.rspsi.editor.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Panel registry plus validated workspace presets for any frontend. */
public record WorkspaceCatalog(
        List<PanelDescriptor> panels,
        List<WorkspaceDefinition> workspaces
) {
    public WorkspaceCatalog {
        panels = List.copyOf(panels == null ? List.of() : panels);
        workspaces = List.copyOf(workspaces == null ? List.of() : workspaces);
        Map<String, PanelDescriptor> byId = new HashMap<>();
        for (PanelDescriptor panel : panels) {
            if (byId.put(panel.id(), panel) != null) {
                throw new IllegalArgumentException("Duplicate panel descriptor: " + panel.id());
            }
        }
        for (WorkspaceDefinition workspace : workspaces) {
            for (PanelPlacement placement : workspace.placements()) {
                PanelDescriptor panel = byId.get(placement.panelId());
                if (panel == null) {
                    throw new IllegalArgumentException("Workspace references unknown panel: " + placement.panelId());
                }
                if (!panel.allows(placement.region())) {
                    throw new IllegalArgumentException("Panel is not allowed in workspace region: "
                            + placement.panelId() + " -> " + placement.region());
                }
            }
        }
    }

    public PanelDescriptor panel(String id) {
        Objects.requireNonNull(id, "id");
        return panels.stream().filter(panel -> panel.id().equals(id)).findFirst().orElseThrow();
    }

    public WorkspaceDefinition workspace(String id) {
        Objects.requireNonNull(id, "id");
        return workspaces.stream().filter(workspace -> workspace.id().equals(id)).findFirst().orElseThrow();
    }
}
