package com.rspsi.studio;

import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdPartyToolPluginIntegrationTest {

    static class CustomFoliageBrushPlugin implements StudioToolPlugin {
        private final AtomicBoolean settingsRendered = new AtomicBoolean(false);
        private final AtomicBoolean drawerRendered = new AtomicBoolean(false);

        @Override
        public String id() {
            return "community.foliage-brush";
        }

        @Override
        public String name() {
            return "Foliage Brush";
        }

        @Override
        public String description() {
            return "Third-party community plugin for painting vegetation and foliage.";
        }

        @Override
        public String version() {
            return "2.0.0";
        }

        @Override
        public String author() {
            return "RuneCommunity";
        }

        @Override
        public String toolId() {
            return "community.foliage";
        }

        @Override
        public String icon() {
            return StudioIcons.BRUSH;
        }

        @Override
        public String shortcut() {
            return "F";
        }

        @Override
        public int railPriority() {
            return 35; // Positioned between Tile Painter (10) and Object Place (50)
        }

        @Override
        public boolean isConfigurable() {
            return true;
        }

        @Override
        public void renderSettings(StudioPanelContext context) {
            settingsRendered.set(true);
        }

        @Override
        public void renderContextDrawer(StudioPanelContext context) {
            drawerRendered.set(true);
        }
    }

    @Test
    void testThirdPartyPluginRegistersAndIntegratesSeamlessly() {
        StudioPluginManager manager = new StudioPluginManager();
        CustomFoliageBrushPlugin plugin = new CustomFoliageBrushPlugin();

        // Register custom 3rd-party tool plugin
        manager.register(plugin);

        // Verify it is discovered in tool plugins
        var toolPlugins = manager.toolPlugins();
        assertTrue(toolPlugins.stream().anyMatch(p -> p.id().equals("community.foliage-brush")));

        // Verify priority sorting
        var foliage = manager.toolPlugin("community.foliage").orElse(null);
        assertNotNull(foliage);
        assertEquals("Foliage Brush", foliage.name());
        assertEquals("F", foliage.shortcut());
        assertEquals(35, foliage.railPriority());
        assertTrue(foliage.isConfigurable());

        // Verify disabling removes it from active toolPlugins
        manager.setEnabled(plugin.id(), false);
        assertFalse(manager.toolPlugins().stream().anyMatch(p -> p.id().equals("community.foliage-brush")));

        // Re-enabling brings it back
        manager.setEnabled(plugin.id(), true);
        assertTrue(manager.toolPlugins().stream().anyMatch(p -> p.id().equals("community.foliage-brush")));

        // Verify settings and drawer rendering hooks can be executed safely
        plugin.renderSettings(null);
        assertTrue(plugin.settingsRendered.get());

        plugin.renderContextDrawer(null);
        assertTrue(plugin.drawerRendered.get());
    }
}
