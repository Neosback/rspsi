package com.rspsi.studio;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.builtin.TileInfoHudPlugin;
import com.rspsi.studio.ui.FloatingToolbar;
import com.rspsi.studio.ui.MinimapTextureService;
import com.rspsi.studio.ui.StudioPanelManager;
import com.rspsi.studio.ui.panels.MinimapPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioPluginsAndMinimapTest {

    @Test
    void testTileInfoHudPluginContract() {
        TileInfoHudPlugin plugin = new TileInfoHudPlugin();
        assertEquals("studio.tile-info-hud", plugin.id());
        assertEquals("Tile Information HUD", plugin.name());
        assertNotNull(plugin.description());
        assertFalse(plugin.description().isBlank());
        assertEquals("1.1.0", plugin.version());
        assertEquals("OpenRune", plugin.author());
        assertNotNull(plugin.icon());
    }

    @Test
    void testStudioPluginManagerRegistrationAndToggle() {
        StudioPluginManager manager = new StudioPluginManager();
        TileInfoHudPlugin plugin = new TileInfoHudPlugin();
        manager.register(plugin);

        assertTrue(manager.isEnabled(plugin.id()));
        manager.setEnabled(plugin.id(), false);
        assertFalse(manager.isEnabled(plugin.id()));
        manager.setEnabled(plugin.id(), true);
        assertTrue(manager.isEnabled(plugin.id()));

        assertEquals(plugin, manager.plugin(plugin.id()).orElse(null));
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
        StudioPluginManager manager = new StudioPluginManager();
        var toolPlugins = manager.toolPlugins();
        assertTrue(toolPlugins.size() >= 5);

        // Check that tools are sorted by priority
        for (int i = 0; i < toolPlugins.size() - 1; i++) {
            assertTrue(toolPlugins.get(i).railPriority() <= toolPlugins.get(i + 1).railPriority());
        }

        // Verify each canonical tool ID can be resolved
        assertTrue(manager.toolPlugin("selection.single").isPresent());
        assertTrue(manager.toolPlugin("selection.multi").isPresent());
        assertTrue(manager.toolPlugin("terrain.tile-painter").isPresent());
        assertTrue(manager.toolPlugin("terrain.raise").isPresent());
        assertTrue(manager.toolPlugin("terrain.smooth").isPresent());
        assertTrue(manager.toolPlugin("terrain.blend").isPresent());
        assertTrue(manager.toolPlugin("terrain.terrace").isPresent());
        assertTrue(manager.toolPlugin("terrain.ramp").isPresent());
        assertTrue(manager.toolPlugin("object.place").isPresent());
        assertTrue(manager.toolPlugin("path.spline").isPresent());

        var pathPlugin = manager.toolPlugin("path.spline").get();
        assertEquals("path.spline", pathPlugin.toolId());
        assertEquals("P", pathPlugin.shortcut());
        assertEquals("Path Builder", pathPlugin.name());
        assertTrue(pathPlugin.isConfigurable());

        var painter = manager.toolPlugin("terrain.tile-painter").get();
        assertEquals("terrain.tile-painter", painter.toolId());
        assertEquals("P", painter.shortcut());
        assertEquals("Tile Painter", painter.name());
        assertTrue(painter.isConfigurable());
    }
}
