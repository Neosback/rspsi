package com.rspsi.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StandardWorkspaceCatalogTest {
    @Test
    void standardCatalogContainsTheFiveControlledWorkspaces() {
        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();

        assertEquals(java.util.List.of("map", "terrain", "objects", "collision", "validation-debug"),
                catalog.workspaces().stream().map(WorkspaceDefinition::id).toList());
        assertEquals(DockRegion.CENTER, catalog.panel("viewport").preferredRegion());
        assertEquals(DockRegion.LEFT, catalog.panel("tools").preferredRegion());
    }

    @Test
    void everyPresetPlacementRespectsPanelConstraints() {
        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();

        for (WorkspaceDefinition workspace : catalog.workspaces()) {
            assertTrue(workspace.placements().stream().anyMatch(p ->
                    p.panelId().equals("viewport") && p.region() == DockRegion.CENTER));
            for (PanelPlacement placement : workspace.placements()) {
                assertTrue(catalog.panel(placement.panelId()).allows(placement.region()));
            }
        }
    }

    @Test
    void presetsKeepOneCenteredViewportAndDoNotDuplicatePanels() {
        WorkspaceCatalog catalog = StandardWorkspaceCatalog.create();

        for (WorkspaceDefinition workspace : catalog.workspaces()) {
            assertEquals(1, workspace.placements().stream()
                    .filter(p -> p.region() == DockRegion.CENTER && p.panelId().equals("viewport")).count());
            assertEquals(workspace.placements().size(), workspace.placements().stream()
                    .map(PanelPlacement::panelId).distinct().count());
        }
    }
}
