package com.rspsi.studio;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.feature.StudioFeatureRegistry;
import com.rspsi.studio.feature.TileInfoHud;
import com.rspsi.studio.ui.FloatingToolbar;
import com.rspsi.studio.ui.MinimapTextureService;
import com.rspsi.studio.ui.StudioPanelManager;
import com.rspsi.studio.ui.panels.MinimapPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioFeaturesAndMinimapTest {

    @Test
    void testTileInfoHudPluginContract() {
        TileInfoHud plugin = new TileInfoHud();
        assertEquals("studio.tile-info-hud", plugin.id());
        assertEquals("Tile Information HUD", plugin.name());
        assertNotNull(plugin.icon());
    }

    @Test
    void testStudioPluginManagerRegistrationAndToggle() {
        StudioFeatureRegistry manager = new StudioFeatureRegistry();
        TileInfoHud plugin = new TileInfoHud();
        manager.register(plugin);

        assertTrue(manager.isEnabled(plugin.id()));
        manager.setEnabled(plugin.id(), false);
        assertFalse(manager.isEnabled(plugin.id()));
        manager.setEnabled(plugin.id(), true);
        assertTrue(manager.isEnabled(plugin.id()));

        assertEquals(plugin, manager.feature(plugin.id()).orElse(null));
    }

    @Test
    void pluginManagerIsNotRegisteredAsRightSidebarPanel() {
        StudioPanelManager panelManager = new StudioPanelManager();
        assertTrue(panelManager.allPanels().stream()
                .noneMatch(panel -> "studio.plugins".equals(panel.id())));
    }

    @Test
    void testMinimapPanelIdAndProperties() {
        MinimapPanel panel = new MinimapPanel();
        assertEquals("studio.minimap", panel.id());
        assertEquals(DockRegion.RIGHT, panel.preferredRegion());
    }

    @Test
    void testFloatingToolbarOffsetAndDragging() {
        FloatingToolbar toolbar = new FloatingToolbar();
        assertEquals(20.0f, toolbar.getOffsetX());
        assertEquals(20.0f, toolbar.getOffsetY());

        toolbar.setOffsetX(45.0f);
        toolbar.setOffsetY(70.0f);
        assertEquals(45.0f, toolbar.getOffsetX());
        assertEquals(70.0f, toolbar.getOffsetY());

        toolbar.resetPosition();
        assertEquals(20.0f, toolbar.getOffsetX());
        assertEquals(20.0f, toolbar.getOffsetY());
    }

    @Test
    void testMinimapTextureServiceDefaults() {
        MinimapTextureService service = new MinimapTextureService();
        assertEquals(256, service.width(0));
        assertEquals(256, service.height(0));
        service.markDirty();
        service.dispose();
    }

    @Test
    void testStudioToolPluginsRegistrationAndOrdering() {
        StudioFeatureRegistry manager = new StudioFeatureRegistry();
        var toolUis = manager.toolUis();
        assertTrue(toolUis.size() >= 5);

        // Check that tools are sorted by priority
        for (int i = 0; i < toolUis.size() - 1; i++) {
            assertTrue(toolUis.get(i).railPriority() <= toolUis.get(i + 1).railPriority());
        }

        // Verify each canonical tool ID can be resolved
        assertTrue(manager.toolUi("selection.single").isPresent());
        assertTrue(manager.toolUi("selection.multi").isPresent());
        assertTrue(manager.toolUi("terrain.tile-painter").isPresent());
        assertTrue(manager.toolUi("terrain.raise").isPresent());
        assertTrue(manager.toolUi("terrain.smooth").isPresent());
        assertTrue(manager.toolUi("terrain.blend").isPresent());
        assertTrue(manager.toolUi("terrain.terrace").isPresent());
        assertTrue(manager.toolUi("terrain.ramp").isPresent());
        assertTrue(manager.toolUi("object.place").isPresent());
        assertTrue(manager.toolUi("path.spline").isPresent());

        var pathPlugin = manager.toolUi("path.spline").get();
        assertEquals("path.spline", pathPlugin.toolId());
        assertEquals("P", pathPlugin.shortcut());
        assertEquals("Path Builder", pathPlugin.name());

        var painter = manager.toolUi("terrain.tile-painter").get();
        assertEquals("terrain.tile-painter", painter.toolId());
        assertEquals("P", painter.shortcut());
        assertEquals("Tile Painter", painter.name());
    }
}
