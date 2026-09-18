package com.rspsi.editor.ui;

import java.util.EnumSet;
import java.util.List;

/** The intentionally small set of supported RSPSi workspace presets. */
public final class StandardWorkspaceCatalog {
    private StandardWorkspaceCatalog() { }

    public static WorkspaceCatalog create() {
        List<PanelDescriptor> panels = List.of(
                panel("tools", DockRegion.LEFT, 72, 320),
                panel("context-toolbar", DockRegion.CENTER, 480, 32),
                panel("viewport", DockRegion.CENTER, 480, 320),
                panel("tool-context", DockRegion.CENTER, 480, 96),
                panel("selector-strip", DockRegion.CENTER, 480, 32),
                panel("right-tool-rail", DockRegion.RIGHT, 52, 320),
                panel("settings-panel", DockRegion.RIGHT, 280, 320),
                panel("outliner", DockRegion.RIGHT, 240, 180),
                panel("inspector", DockRegion.RIGHT, 260, 320),
                panel("floor-palette", DockRegion.BOTTOM, 320, 180),
                panel("assets", DockRegion.BOTTOM, 320, 180),
                panel("history", DockRegion.BOTTOM, 280, 180),
                panel("validation", DockRegion.BOTTOM, 320, 180),
                panel("console", DockRegion.BOTTOM, 320, 140),
                panel("plugins", DockRegion.BOTTOM, 300, 160),
                new PanelDescriptor("command-palette", DockRegion.OVERLAY,
                        EnumSet.of(DockRegion.OVERLAY), 360, 240));
        List<WorkspaceDefinition> workspaces = List.of(
                workspace("map", "tools", "context-toolbar", "viewport", "tool-context", "selector-strip",
                        "right-tool-rail", "settings-panel", "floor-palette", "assets", "history", "validation", "console", "plugins"));
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
                        case "context-toolbar", "viewport", "tool-context", "selector-strip" -> DockRegion.CENTER;
                        case "right-tool-rail", "settings-panel" -> DockRegion.RIGHT;
                        case "outliner", "inspector" -> DockRegion.OVERLAY;
                        default -> DockRegion.BOTTOM;
                    };
                    return new PanelPlacement(panelId, region, index);
                }).toList();
        return new WorkspaceDefinition(id, placements);
    }
}
