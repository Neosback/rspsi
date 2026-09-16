package com.rspsi.editor.ui;

import java.util.EnumSet;
import java.util.List;

/** The intentionally small set of supported RSPSi workspace presets. */
public final class StandardWorkspaceCatalog {
    private StandardWorkspaceCatalog() { }

    public static WorkspaceCatalog create() {
        List<PanelDescriptor> panels = List.of(
                panel("tools", DockRegion.LEFT, 180, 320),
                panel("viewport", DockRegion.CENTER, 480, 320),
                panel("inspector", DockRegion.RIGHT, 260, 320),
                panel("assets", DockRegion.BOTTOM, 320, 180),
                panel("history", DockRegion.BOTTOM, 280, 180),
                panel("validation", DockRegion.BOTTOM, 320, 180),
                panel("console", DockRegion.BOTTOM, 320, 140),
                new PanelDescriptor("command-palette", DockRegion.OVERLAY,
                        EnumSet.of(DockRegion.OVERLAY), 360, 240));
        List<WorkspaceDefinition> workspaces = List.of(
                workspace("map", "tools", "viewport", "inspector", "assets", "history"),
                workspace("terrain", "tools", "viewport", "inspector", "assets", "history"),
                workspace("objects", "tools", "viewport", "inspector", "assets", "history"),
                workspace("collision", "tools", "viewport", "inspector", "validation", "console"),
                workspace("validation-debug", "tools", "viewport", "inspector", "validation", "console"));
        return new WorkspaceCatalog(panels, workspaces);
    }

    private static PanelDescriptor panel(String id, DockRegion region, int width, int height) {
        return new PanelDescriptor(id, region, EnumSet.of(region), width, height);
    }

    private static WorkspaceDefinition workspace(String id, String... panelIds) {
        List<PanelPlacement> placements = java.util.stream.IntStream.range(0, panelIds.length)
                .mapToObj(index -> {
                    String panelId = panelIds[index];
                    DockRegion region = switch (panelId) {
                        case "tools" -> DockRegion.LEFT;
                        case "viewport" -> DockRegion.CENTER;
                        case "inspector" -> DockRegion.RIGHT;
                        default -> DockRegion.BOTTOM;
                    };
                    return new PanelPlacement(panelId, region, index);
                }).toList();
        return new WorkspaceDefinition(id, placements);
    }
}
