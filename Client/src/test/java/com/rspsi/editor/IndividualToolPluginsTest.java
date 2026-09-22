package com.rspsi.editor;

import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPanelRegistration;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.builtin.CoreToolsPlugin;
import com.rspsi.editor.ui.DockRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndividualToolPluginsTest {

    @Test
    void allTwentyToolPluginsInitializeIndependently() {
        List<EditorPlugin> plugins = CoreToolsPlugin.individualToolPlugins();
        assertEquals(22, plugins.size());

        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        try (EditorPluginHost host = EditorPluginHost.initialize(plugins, session, EmptyAssetRepository.INSTANCE)) {
            assertEquals(22, host.plugins().size());
            List<EditorToolRegistration> tools = host.registry().toolRegistrations();
            assertEquals(22, tools.size());

            // Check groups
            Set<String> groups = Set.of("Selector", "Paint", "Height", "Objects", "Path");
            for (EditorToolRegistration tool : tools) {
                assertNotNull(tool.id());
                assertNotNull(tool.label());
                assertTrue(groups.contains(tool.toolGroup()), "Unexpected tool group: " + tool.toolGroup());
                assertNotNull(host.registry().createTool(tool.id()));
            }

            // Verify specific tools
            assertNotNull(host.registry().createTool("selection.box"));
            assertNotNull(host.registry().createTool("selection.lasso"));
            assertNotNull(host.registry().createTool("terrain.paint-overlay"));
            assertNotNull(host.registry().createTool("terrain.paint-underlay"));
            assertNotNull(host.registry().createTool("terrain.tile-painter"));
            assertNotNull(host.registry().createTool("path.spline"));
            assertNotNull(host.registry().createTool("terrain.raise"));
            assertNotNull(host.registry().createTool("terrain.lower"));
            assertNotNull(host.registry().createTool("object.place"));
            assertNotNull(host.registry().createTool("object.delete"));
        }
    }

    @Test
    void panelRegistrationsSupportDockRegions() {
        EditorPanelRegistration reg = new EditorPanelRegistration(
                "custom.panel", "Custom Panel", "CUSTOM_ICON",
                DockRegion.RIGHT, 10
        );

        assertEquals("custom.panel", reg.id());
        assertEquals("Custom Panel", reg.title());
        assertEquals(DockRegion.RIGHT, reg.preferredRegion());
        assertTrue(reg.allows(DockRegion.RIGHT));
        assertTrue(reg.allows(DockRegion.BOTTOM));
    }
}
