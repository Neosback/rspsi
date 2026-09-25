package com.rspsi.studio;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.ui.StudioBottomBar;
import com.rspsi.studio.ui.panels.HeightToolPanel;
import com.rspsi.studio.ui.panels.KnowledgePanel;
import com.rspsi.studio.ui.panels.MapSettingsPanel;
import com.rspsi.studio.ui.panels.MinimapPanel;
import com.rspsi.studio.ui.panels.ObjectViewerPanel;
import com.rspsi.studio.ui.panels.OutlinerPanel;
import com.rspsi.studio.ui.panels.PlayerStatePanel;
import com.rspsi.studio.ui.panels.TileBrushPanel;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Architecture memory for the Map Studio surface ownership contract. */
class WorkspaceSurfaceOwnershipTest {

    @Test
    void inspectionAndSettingsPanelsAreRightRailOnly() {
        var panels = java.util.List.of(
                new TileBrushPanel(),
                new MinimapPanel(),
                new ObjectViewerPanel(),
                new OutlinerPanel(),
                new KnowledgePanel(),
                new PlayerStatePanel(),
                new MapSettingsPanel());

        panels.forEach(panel -> {
            assertEquals(DockRegion.RIGHT, panel.preferredRegion(), panel.id());
            assertEquals(Set.of(DockRegion.RIGHT), panel.allowedRegions(), panel.id());
        });
    }

    @Test
    void authoringPalettesAreBottomDrawerOnly() {
        var panels = java.util.List.of(new TilePainterPalette(), new HeightToolPanel());
        panels.forEach(panel -> {
            assertEquals(DockRegion.BOTTOM, panel.preferredRegion(), panel.id());
            assertEquals(Set.of(DockRegion.BOTTOM), panel.allowedRegions(), panel.id());
        });
    }

    @Test
    void bottomBarHasNoGenericConsoleModesOrHardcodedFallbackToolRegistry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rspsi/studio/ui/StudioBottomBar.java"));

        for (String retired : java.util.List.of(
                "DrawerMode",
                "renderHistoryDrawer",
                "renderTasksDrawer",
                "renderNotificationsDrawer",
                "renderDiagnosticsDrawer",
                "renderFallbackToolButtons",
                "Fill Overlay on Selection",
                "Build Flat Road")) {
            assertFalse(source.contains(retired),
                    "Bottom authoring surface regained retired path: " + retired);
        }
    }
}
