package com.rspsi.editor;

import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.core.CoreEditorModules;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreEditorModulesTest {

    @Test
    void canonicalCoreManifestInstallsSevenModulesAndTwentyFourTools() {
        EditorSession session = new EditorSession(new WorldModel(8, 8, 1));

        try (EditorPluginHost host = EditorPluginHost.initializeWithCoreModules(
                CoreEditorModules.all(),
                List.of(),
                session,
                EmptyAssetRepository.INSTANCE)) {

            assertEquals(7, host.coreModuleIds().size());
            assertEquals(Set.copyOf(CoreEditorModules.ids()), Set.copyOf(host.coreModuleIds()));
            assertTrue(host.plugins().isEmpty(),
                    "core editor modules must not appear as optional plugins");

            var tools = host.registry().toolRegistrations();
            assertEquals(24, tools.size(), "core tool inventory changed outside the canonical manifest");

            for (String id : List.of(
                    "selection.box",
                    "selection.lasso",
                    "terrain.paint-overlay",
                    "terrain.paint-underlay",
                    "terrain.tile-painter",
                    "terrain.blend",
                    "terrain.terrace",
                    "path.spline",
                    "object.place",
                    "object.delete")) {
                assertNotNull(host.registry().createTool(id), "missing core tool: " + id);
            }

            assertTrue(host.registry().uiSurfaceContributions().stream()
                    .anyMatch(surface -> "studio.tile-palette".equals(surface.id())));
            assertTrue(host.registry().uiSurfaceContributions().stream()
                    .anyMatch(surface -> "studio.path-context".equals(surface.id())));
        }
    }

    @Test
    void legacyCoreHostIdentitiesRemainAvailableForExtensionCompatibility() {
        assertEquals(Set.of(
                        "rspsi.tools.terrain",
                        "rspsi.tools.terrain.painter",
                        "rspsi.tools.path.spline",
                        "rspsi.tools.objects",
                        "rspsi.tools.selection",
                        "rspsi.tools.renderer-debug",
                        "rspsi.ui.core-surfaces"),
                CoreEditorModules.hostVersions().keySet());
        CoreEditorModules.hostVersions().values()
                .forEach(version -> assertEquals("0.1.0", version));
    }
}
