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
        // Default: all heights visible, no hidden/empty tiles
        assertTrue(config.allHeightsVisible());
        assertEquals(0, config.currentHeight());
        assertFalse(config.hiddenTilesVisible());
        assertFalse(config.emptyTilesVisible());
        // 4 is the registry default for MSAA
        assertEquals(4, config.msaaSamples());
        assertEquals(BackfacePolicy.NativeCullingMode.CLIENT_FRONT,
                RenderSettingKeys.registry().defaults().get(RenderSettingKeys.NATIVE_CULLING_MODE));
        assertEquals(GpuDebugView.NONE, config.gpuDebugView());
        assertEquals(BackfacePolicy.NativeCullingMode.CLIENT_FRONT, config.nativeCullingMode());
    }

    @Test
    void settingsCompileToVisibilityPolicyWithMaxPlaneExclusiveLogic() {
        SettingsRegistry registry = RenderSettingKeys.registry();
        SettingsSnapshot snapshot = registry.defaults()
                .with(RenderSettingKeys.CURRENT_HEIGHT, 2)
                .with(RenderSettingKeys.ALL_HEIGHTS_VISIBLE, false)
                .with(RenderSettingKeys.BRIDGE_TILES_VISIBLE, false)
                .with(RenderSettingKeys.EXPOSURE, 1.25);

        RenderConfig config = new RenderConfigCompiler().compile(snapshot);

        assertEquals(2, config.currentHeight());
        assertFalse(config.allHeightsVisible());
        assertEquals(1.25, config.exposure());
        assertFalse(config.bridgeTilesVisible());
        // maxPlaneExclusive(4) with currentHeight=2, allHeights=false → 3
        assertEquals(3, config.visibilityPolicy().maxPlaneExclusive(4));
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
        // tile with authoredPlane=2, effectivePlane=1, cullLevel=0
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

        // currentHeight=0, allHeightsVisible=false → only plane 0 visible
        // but authored plane 2 >= maxPlaneExclusive(4)=1, so tile is filtered
        // Change to allHeightsVisible=true so tile passes, but terrain is hidden
        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.CURRENT_HEIGHT, 3)
                        .with(RenderSettingKeys.ALL_HEIGHTS_VISIBLE, true)
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
                RenderSettingKeys.GROUND_DECORATIONS_VISIBLE.id(),
                RenderSettingKeys.BRIDGE_TILES_VISIBLE.id(), RenderSettingKeys.HIDDEN_TILES_VISIBLE.id(),
                RenderSettingKeys.EMPTY_TILES_VISIBLE.id(), RenderSettingKeys.INVISIBLE_OBJECTS_VISIBLE.id(),
                RenderSettingKeys.COLLISION_VISIBLE.id(), RenderSettingKeys.WIREFRAME.id(),
                RenderSettingKeys.CURRENT_HEIGHT.id(), RenderSettingKeys.ALL_HEIGHTS_VISIBLE.id(),
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
    void renderConfigConsumesTransientRoofRemovalStateWithoutPersistingIt() {        com.rspsi.editor.model.TileCoordinate lowerCoordinate =
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

        // currentHeight=0, allHeightsVisible=false → only plane 0 visible (upper at plane 1 filtered)
        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.CURRENT_HEIGHT, 0)
                        .with(RenderSettingKeys.ALL_HEIGHTS_VISIBLE, false));

        RoofRemovalState state = new RoofRemovalState(
                RoofRemovalState.POSITION,
                new RoofRemovalState.ScenePoint(1, 1),
                null, null, null, 200);

        assertEquals(state, config.visibilityPolicy(state).roofRemovalState());
        GpuScenePacket filtered = config.apply(packet);

        // With allHeightsVisible=false and currentHeight=0, only plane 0 passes
        assertEquals(1, filtered.tiles().size());
        assertEquals(0, filtered.tiles().get(0).authoredPlane());
        assertEquals(RoofRemovalState.disabled(), config.visibilityPolicy().roofRemovalState(),
                "compiled settings remain free of transient player/camera state");
    }

    @Test
    void forPlanPreservesHiddenAndEmptyFlagsSoTogglesRebuildGeometry() {
        RenderConfig toggled = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.HIDDEN_TILES_VISIBLE, true)
                        .with(RenderSettingKeys.EMPTY_TILES_VISIBLE, true));
        assertTrue(toggled.forPlan().hiddenTilesVisible());
        assertTrue(toggled.forPlan().emptyTilesVisible());

        RenderConfig defaults = new RenderConfigCompiler().defaultConfig();
        assertFalse(defaults.forPlan().hiddenTilesVisible());
        assertFalse(defaults.forPlan().emptyTilesVisible());
    }

    @Test
    void applyStripsPlaceholdersAndRetintsHiddenGroundPerFlags() {
        com.rspsi.editor.model.TileCoordinate greyCoordinate =
                new com.rspsi.editor.model.TileCoordinate(0, 10, 10);
        TerrainRenderPacket grey = new TerrainRenderPacket(greyCoordinate,
                List.of(new TerrainRenderVertex(0, 0, 0, 47031, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, 47031, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, 47031, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, -1, -1, false, false, -1);
        com.rspsi.editor.model.TileCoordinate hiddenCoordinate =
                new com.rspsi.editor.model.TileCoordinate(0, 20, 20);
        TerrainRenderPacket hiddenGround = new TerrainRenderPacket(hiddenCoordinate,
                List.of(new TerrainRenderVertex(0, 0, 12, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 12, 101, 128, 0),
                        new TerrainRenderVertex(0, 128, 16, 102, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -2, false, true, -2);
        SceneTileSnapshot greyTile = snapshot(greyCoordinate, grey);
        SceneTileSnapshot hiddenTile = snapshot(hiddenCoordinate, hiddenGround);
        SceneWindow window = new SceneWindow(
                new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1, Map.of()),
                0, 0, 4, 0, java.util.Set.of(), List.of());
        GpuScenePacket packet = new GpuScenePacket(
                window, List.of(greyTile, hiddenTile), LightingProfile.osrs(),
                "render-config-placeholder-flags", Map.of());

        RenderConfig off = new RenderConfigCompiler().defaultConfig();
        GpuScenePacket stripped = off.apply(packet);
        // Both tiles stay in the packet (tile existence feeds scene APIs);
        // the grey placeholder loses its faces while hidden ground is untouched.
        assertEquals(2, stripped.tiles().size());
        assertTrue(stripped.tiles().get(0).terrain().orElseThrow().faces().isEmpty());
        assertTrue(stripped.tiles().get(1).terrain().orElseThrow().vertices().stream()
                .anyMatch(vertex -> vertex.packedHsl() == 100));

        RenderConfig on = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.HIDDEN_TILES_VISIBLE, true)
                        .with(RenderSettingKeys.EMPTY_TILES_VISIBLE, true));
        GpuScenePacket shown = on.apply(packet);
        assertEquals(2, shown.tiles().size());
        assertEquals(1, shown.tiles().get(0).terrain().orElseThrow().faces().size());
        assertTrue(shown.tiles().get(1).terrain().orElseThrow().vertices().stream()
                .allMatch(vertex -> vertex.packedHsl() == OsrsTerrainColorMath.HIDDEN_HIGHLIGHT_HSL));
    }

    private static SceneTileSnapshot snapshot(com.rspsi.editor.model.TileCoordinate coordinate,
                                              TerrainRenderPacket terrain) {
        return new SceneTileSnapshot(
                coordinate,
                com.rspsi.editor.model.WorldTileAddress.of(
                        coordinate.x(), coordinate.y(), coordinate.plane()),
                0, coordinate.plane(), coordinate.plane(), coordinate.plane(),
                coordinate.plane(), Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);
    }
}
