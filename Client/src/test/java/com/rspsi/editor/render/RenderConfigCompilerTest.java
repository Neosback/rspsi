package com.rspsi.editor.render;

import com.rspsi.editor.settings.SettingInvalidation;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingsRegistry;
import com.rspsi.editor.settings.SettingsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertEquals(SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL, config.planeSelection());
        // 4 is the registry default now that MSAA is implemented end-to-end
        // (GlFramebuffer); it was 0 only while the setting was clamped
        // unavailable pending the FBO acceptance gate.
        assertEquals(4, config.msaaSamples());
        assertEquals(BackfacePolicy.NativeCullingMode.CLIENT_FRONT,
                RenderSettingKeys.registry().defaults().get(RenderSettingKeys.NATIVE_CULLING_MODE));
        assertEquals(GpuDebugView.NONE, config.gpuDebugView());
        assertEquals(BackfacePolicy.NativeCullingMode.CLIENT_FRONT, config.nativeCullingMode());
    }

    @Test
    void settingsCompileToOneVisibilityPolicyWithoutChangingAuthoredData() {
        SettingsRegistry registry = RenderSettingKeys.registry();
        SettingsSnapshot snapshot = registry.defaults()
                .with(RenderSettingKeys.ACTIVE_PLANE, 2)
                .with(RenderSettingKeys.PLANE_SELECTION,
                        SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL)
                .with(RenderSettingKeys.ROOFS_VISIBLE, false)
                .with(RenderSettingKeys.BRIDGE_TILES_VISIBLE, false)
                .with(RenderSettingKeys.EXPOSURE, 1.25);

        RenderConfig config = new RenderConfigCompiler().compile(snapshot);

        assertEquals(2, config.activePlane());
        assertEquals(1.25, config.exposure());
        assertFalse(config.roofsVisible());
        assertFalse(config.bridgeTilesVisible());
        assertEquals(SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL,
                config.visibilityPolicy().planeSelection());
        assertEquals(2, config.visibilityPolicy().selectedPlane());
        assertTrue(config.visibilityPolicy().hideRoofGeometry());
        assertTrue(config.visibilityPolicy().hideBridgeUpperGeometry());
    }

    @Test
    void gpuDebugViewCompilesIntoPresentationOnly() {
        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.GPU_DEBUG_VIEW, GpuDebugView.NORMALS));

        assertEquals(GpuDebugView.NORMALS, config.gpuDebugView());
        assertEquals(GpuDebugView.NORMALS, config.presentation().debugView());
    }

    @Test
    void renderConfigFilteringPreservesExplicitScenePlaneTuple() {
        com.rspsi.editor.model.TileCoordinate coordinate =
                new com.rspsi.editor.model.TileCoordinate(2, 3200, 3200);
        com.rspsi.editor.model.WorldTileAddress address =
                com.rspsi.editor.model.WorldTileAddress.of(3200, 3200, 2);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        SceneTileSnapshot sourceTile = new SceneTileSnapshot(
                coordinate, address, 0,
                1, 2, 2, 0,
                Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, true);
        SceneWindow window = new SceneWindow(
                new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1, Map.of()),
                3200, 3200, 4, 0, java.util.Set.of(), List.of());
        GpuScenePacket packet = new GpuScenePacket(
                window, List.of(sourceTile), LightingProfile.osrs(),
                "render-config-plane-semantics", Map.of());

        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.ACTIVE_PLANE, 0)
                        .with(RenderSettingKeys.PLANE_SELECTION,
                                SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL)
                        .with(RenderSettingKeys.TERRAIN_VISIBLE, false));

        GpuScenePacket filtered = config.apply(packet);

        assertEquals(1, filtered.tiles().size());
        SceneTileSnapshot result = filtered.tiles().get(0);
        assertFalse(result.terrain().isPresent());
        assertEquals(2, result.authoredPlane());
        assertEquals(1, result.effectivePlane());
        assertEquals(2, result.renderLevel());
        assertEquals(0, result.planeCullLevel());
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
                RenderSettingKeys.INVISIBLE_OBJECTS_VISIBLE.id(),
                RenderSettingKeys.COLLISION_VISIBLE.id(), RenderSettingKeys.WIREFRAME.id(),
                RenderSettingKeys.ACTIVE_PLANE.id(), RenderSettingKeys.PLANE_SELECTION.id(),
                RenderSettingKeys.BRIGHTNESS.id(), RenderSettingKeys.EXPOSURE.id(),
                RenderSettingKeys.MSAA_SAMPLES.id(), RenderSettingKeys.FOG_DEPTH_TILES.id(),
                RenderSettingKeys.FOG_COLOR.id(), RenderSettingKeys.NATIVE_CULLING_MODE.id(),
                RenderSettingKeys.GPU_DEBUG_VIEW.id());

        assertEquals(handled, renderConfigKeys);
        SettingsRegistry registry = RenderSettingKeys.registry();
        assertTrue(registry.specifications().stream()
                .allMatch(specification -> !specification.invalidations().contains(SettingInvalidation.SCENE)
                        || specification.scope() == SettingScope.GLOBAL
                        || specification.scope() == SettingScope.PROJECT
                        || specification.scope() == SettingScope.VIEWPORT
                        || specification.scope() == SettingScope.TRANSIENT));
    }
    @Test
    void renderConfigConsumesTransientRoofRemovalStateWithoutPersistingIt() {
        com.rspsi.editor.model.TileCoordinate lowerCoordinate =
                new com.rspsi.editor.model.TileCoordinate(0, 1, 1);
        SceneTileSnapshot lower = new SceneTileSnapshot(
                lowerCoordinate,
                com.rspsi.editor.model.WorldTileAddress.of(1, 1, 0),
                com.rspsi.editor.model.OsrsTileFlags.REMOVE_ROOFS,
                0, 0, 0, 0,
                Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
                false, false);
        com.rspsi.editor.model.TileCoordinate upperCoordinate =
                new com.rspsi.editor.model.TileCoordinate(1, 1, 1);
        SceneTileSnapshot upper = new SceneTileSnapshot(
                upperCoordinate,
                com.rspsi.editor.model.WorldTileAddress.of(1, 1, 1),
                0,
                1, 1, 1, 1,
                Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
                false, false);

        com.rspsi.editor.model.WorldRegionWindow source =
                new com.rspsi.editor.model.WorldRegionWindow(0, 0, 1, 1, Map.of());
        SceneWindow window = new SceneWindow(
                source, 0, 0, 4, 0, 0, -1, Set.of(), List.of());
        GpuScenePacket packet = new GpuScenePacket(
                window, List.of(lower, upper), LightingProfile.osrs(),
                "render-config-roof-state", Map.of());

        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.ACTIVE_PLANE, 0)
                        .with(RenderSettingKeys.PLANE_SELECTION,
                                SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL));
        RoofRemovalState state = new RoofRemovalState(
                RoofRemovalState.POSITION,
                new RoofRemovalState.ScenePoint(1, 1),
                null, null, null, 200);

        assertEquals(state, config.visibilityPolicy(state).roofRemovalState());
        GpuScenePacket filtered = config.apply(packet, state);

        assertEquals(1, filtered.tiles().size());
        assertEquals(0, filtered.tiles().get(0).authoredPlane());
        assertEquals(RoofRemovalState.disabled(), config.visibilityPolicy().roofRemovalState(),
                "compiled settings remain free of transient player/camera state");
    }

}
