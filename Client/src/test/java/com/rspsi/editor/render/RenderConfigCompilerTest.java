package com.rspsi.editor.render;

import com.rspsi.editor.settings.SettingInvalidation;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingsRegistry;
import com.rspsi.editor.settings.SettingsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderConfigCompilerTest {
    @Test
    void defaultRegistryCompilesVanillaCompatibilityConfig() {
        RenderConfig config = new RenderConfigCompiler().defaultConfig();

        assertEquals(RenderProfile.VANILLA_COMPATIBILITY, config.profile());
        assertTrue(config.terrainVisible());
        assertTrue(config.objectsVisible());
        assertTrue(config.roofsVisible());
        assertEquals(SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE, config.planeSelection());
        // 4 is the registry default now that MSAA is implemented end-to-end
        // (GlFramebuffer); it was 0 only while the setting was clamped
        // unavailable pending the FBO acceptance gate.
        assertEquals(4, config.msaaSamples());
    }

    @Test
    void settingsCompileToOneVisibilityPolicyWithoutChangingAuthoredData() {
        SettingsRegistry registry = RenderSettingKeys.registry();
        SettingsSnapshot snapshot = registry.defaults()
                .with(RenderSettingKeys.ACTIVE_PLANE, 2)
                .with(RenderSettingKeys.PLANE_SELECTION,
                        SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE)
                .with(RenderSettingKeys.ROOFS_VISIBLE, false)
                .with(RenderSettingKeys.BRIDGE_TILES_VISIBLE, false)
                .with(RenderSettingKeys.EXPOSURE, 1.25);

        RenderConfig config = new RenderConfigCompiler().compile(snapshot);

        assertEquals(2, config.activePlane());
        assertEquals(1.25, config.exposure());
        assertFalse(config.roofsVisible());
        assertFalse(config.bridgeTilesVisible());
        assertEquals(SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE,
                config.visibilityPolicy().planeSelection());
        assertEquals(2, config.visibilityPolicy().selectedPlane());
        assertTrue(config.visibilityPolicy().hideRoofGeometry());
        assertTrue(config.visibilityPolicy().hideBridgeUpperGeometry());
    }

    @Test
    void everyRegisteredCoreRenderSettingHasACompilerDestination() {
        Set<String> renderConfigKeys = RenderSettingKeys.consumerCatalog().consumers().get("render-config").stream()
                .map(com.rspsi.editor.settings.SettingKey::id).collect(java.util.stream.Collectors.toSet());
        Set<String> handled = Set.of(
                RenderSettingKeys.PROFILE.id(), RenderSettingKeys.TERRAIN_VISIBLE.id(),
                RenderSettingKeys.OBJECTS_VISIBLE.id(), RenderSettingKeys.WALLS_VISIBLE.id(),
                RenderSettingKeys.WALL_DECORATIONS_VISIBLE.id(), RenderSettingKeys.GROUND_OBJECTS_VISIBLE.id(),
                RenderSettingKeys.GROUND_DECORATIONS_VISIBLE.id(), RenderSettingKeys.ROOFS_VISIBLE.id(),
                RenderSettingKeys.BRIDGE_TILES_VISIBLE.id(), RenderSettingKeys.HIDDEN_TILES_VISIBLE.id(),
                RenderSettingKeys.COLLISION_VISIBLE.id(), RenderSettingKeys.WIREFRAME.id(),
                RenderSettingKeys.ACTIVE_PLANE.id(), RenderSettingKeys.PLANE_SELECTION.id(),
                RenderSettingKeys.BRIGHTNESS.id(), RenderSettingKeys.EXPOSURE.id(),
                RenderSettingKeys.MSAA_SAMPLES.id(), RenderSettingKeys.FOG_DEPTH_TILES.id(),
                RenderSettingKeys.FOG_COLOR.id());

        assertEquals(handled, renderConfigKeys);
        SettingsRegistry registry = RenderSettingKeys.registry();
        assertTrue(registry.specifications().stream()
                .allMatch(specification -> !specification.invalidations().contains(SettingInvalidation.SCENE)
                        || specification.scope() == SettingScope.GLOBAL
                        || specification.scope() == SettingScope.PROJECT
                        || specification.scope() == SettingScope.VIEWPORT
                        || specification.scope() == SettingScope.TRANSIENT));
    }
}
