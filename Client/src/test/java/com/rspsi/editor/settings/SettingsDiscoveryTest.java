package com.rspsi.editor.settings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDiscoveryTest {

    @Test
    void testCategoriesAndCategorizedSorting() {
        SettingsRegistry registry = new SettingsRegistry();

        SettingKey<Integer> k1 = new SettingKey<>("render.msaa", Integer.class);
        SettingKey<Boolean> k2 = new SettingKey<>("render.shadows", Boolean.class);
        SettingKey<String> k3 = new SettingKey<>("ui.theme", String.class);

        SettingSpec<Integer> s1 = SettingSpec.integer(k1, 4, 1, 16, SettingScope.GLOBAL,
                "Rendering", "MSAA", "Antialiasing samples", Set.of()).withOrder(2);
        SettingSpec<Boolean> s2 = SettingSpec.of(k2, true, SettingScope.GLOBAL,
                "Rendering", "Shadows", "Enable terrain shadows", Set.of()).withOrder(1);
        SettingSpec<String> s3 = SettingSpec.of(k3, "dark", SettingScope.GLOBAL,
                "Theme", "App Theme", "Color scheme", Set.of()).withOrder(0);

        registry.register(s1);
        registry.register(s2);
        registry.register(s3);

        Set<String> categories = registry.categories();
        assertEquals(Set.of("Rendering", "Theme"), categories);

        Map<String, List<SettingSpec<?>>> map = registry.categorized();
        assertEquals(2, map.size());

        List<SettingSpec<?>> renderingList = map.get("Rendering");
        assertEquals(2, renderingList.size());
        // s2 order=1 comes before s1 order=2
        assertEquals("render.shadows", renderingList.get(0).key().id());
        assertEquals("render.msaa", renderingList.get(1).key().id());

        List<SettingSpec<?>> themeList = map.get("Theme");
        assertEquals(1, themeList.size());
        assertEquals("ui.theme", themeList.get(0).key().id());
    }

    @Test
    void testSearchSettings() {
        SettingsRegistry registry = new SettingsRegistry();

        SettingKey<Boolean> wireframe = new SettingKey<>("view.wireframe", Boolean.class);
        SettingKey<Integer> fov = new SettingKey<>("camera.fov", Integer.class);

        registry.register(SettingSpec.of(wireframe, false, SettingScope.VIEWPORT,
                "View", "Wireframe", "Display mesh triangles", Set.of()));
        registry.register(SettingSpec.integer(fov, 60, 30, 110, SettingScope.GLOBAL,
                "Camera", "Field of View", "Vertical angle in degrees", Set.of()));

        // Empty search returns all
        assertEquals(2, registry.search("").size());
        assertEquals(2, registry.search(null).size());

        // Match by ID
        List<SettingSpec<?>> byId = registry.search("wireframe");
        assertEquals(1, byId.size());
        assertEquals("view.wireframe", byId.get(0).key().id());

        // Match by label
        List<SettingSpec<?>> byLabel = registry.search("field");
        assertEquals(1, byLabel.size());
        assertEquals("camera.fov", byLabel.get(0).key().id());

        // Match by description
        List<SettingSpec<?>> byDesc = registry.search("mesh triangles");
        assertEquals(1, byDesc.size());
        assertEquals("view.wireframe", byDesc.get(0).key().id());

        // Match by category
        List<SettingSpec<?>> byCat = registry.search("camera");
        assertEquals(1, byCat.size());
        assertEquals("camera.fov", byCat.get(0).key().id());

        // No match
        assertTrue(registry.search("nonexistent_term").isEmpty());
    }

    @Test
    void testSettingSpecBuilderWithCategoryAndOrder() {
        SettingKey<Boolean> key = new SettingKey<>("flag.debug", Boolean.class);
        SettingSpec<Boolean> spec = SettingSpec.of(key, true, SettingScope.GLOBAL, "Debug", "Debug mode", Set.of());

        assertEquals("General", spec.category());
        assertEquals(0, spec.order());

        SettingSpec<Boolean> updated = spec.withCategory("Diagnostics").withOrder(42);
        assertEquals("Diagnostics", updated.category());
        assertEquals(42, updated.order());
    }
}
