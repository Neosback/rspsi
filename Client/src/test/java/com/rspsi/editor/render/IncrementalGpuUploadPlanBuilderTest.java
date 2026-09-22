package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalGpuUploadPlanBuilderTest {
    @Test
    void reusesUnchangedTileFragmentsAndMatchesFullFlattening() {
        SceneTileSnapshot first = terrainTile(7, 4, 100);
        SceneTileSnapshot second = terrainTile(8, 4, 200);
        GpuScenePacket initial = packet(List.of(first, second), "initial");

        IncrementalGpuUploadPlanBuilder incremental = new IncrementalGpuUploadPlanBuilder();
        var seeded = incremental.buildInitial(initial);
        assertEquals(2, seeded.rebuiltTiles());
        assertEquals(0, seeded.reusedTiles());
        assertEquals(2, seeded.rebuiltZones());
        assertEquals(0, seeded.reusedZones());
        assertEquals(0, seeded.plan().flatMaterializationCount());
        assertFalse(seeded.plan().flatMaterialized());

        SceneTileSnapshot changedSecond = terrainTile(8, 4, 300);
        GpuScenePacket changed = packet(List.of(first, changedSecond), "changed");
        Set<WorldZoneCoordinate> dirty =
                Set.of(WorldZoneCoordinate.from(changedSecond.worldAddress()));

        var update = incremental.build(changed, dirty);
        GpuUploadPlan expected = new GpuUploadPlanBuilder().build(changed);

        assertEquals(1, update.rebuiltTiles());
        assertEquals(1, update.reusedTiles());
        assertEquals(1, update.rebuiltZones());
        assertEquals(1, update.reusedZones());
        assertEquals(0, update.plan().flatMaterializationCount());
        assertFalse(update.plan().flatMaterialized());
        assertEquals(expected.vertices(), update.plan().vertices());
        assertEquals(expected.indices(), update.plan().indices());
        assertEquals(expected.commands(), update.plan().commands());
        assertEquals(expected.textureTriangles(), update.plan().textureTriangles());
        assertEquals(expected.occluders(), update.plan().occluders());
        assertEquals(expected.fingerprint(), update.plan().fingerprint());
        assertEquals(1, update.plan().flatMaterializationCount());
        assertTrue(update.plan().flatMaterialized());
        assertNotEquals(seeded.plan().fingerprint(), update.plan().fingerprint());
    }

    @Test
    void rebuiltObjectFragmentRetainsStableSceneIdentity() {
        SceneTileSnapshot initialTile = objectTile(1, 1, 42);
        IncrementalGpuUploadPlanBuilder incremental = new IncrementalGpuUploadPlanBuilder();

        var seeded = incremental.buildInitial(packet(List.of(initialTile), "identity-initial"));
        SceneObjectIdentity initialIdentity = seeded.plan().commands().get(0).sceneObjectIdentity();

        SceneTileSnapshot rebuiltTile = objectTile(1, 1, 42);
        var rebuilt = incremental.build(packet(List.of(rebuiltTile), "identity-rebuilt"),
                Set.of(WorldZoneCoordinate.from(rebuiltTile.worldAddress())));

        assertEquals(1, rebuilt.rebuiltTiles());
        assertEquals(initialIdentity, rebuilt.plan().commands().get(0).sceneObjectIdentity());
        assertEquals(initialIdentity.stableId(),
                rebuilt.plan().commands().get(0).sceneObjectIdentity().stableId());
    }

    @Test
    void assemblyPreservesFullBuilderMergingInsideOneWorldZone() {
        SceneTileSnapshot first = terrainTile(1, 1, 100);
        SceneTileSnapshot second = terrainTile(1, 2, 100);
        GpuScenePacket packet = packet(List.of(first, second), "same-zone");

        GpuUploadPlan expected = new GpuUploadPlanBuilder().build(packet);
        GpuUploadPlan actual = new IncrementalGpuUploadPlanBuilder()
                .buildInitial(packet).plan();

        assertEquals(1, expected.commands().size());
        assertEquals(expected.vertices(), actual.vertices());
        assertEquals(expected.indices(), actual.indices());
        assertEquals(expected.commands(), actual.commands());
        assertEquals(expected.fingerprint(), actual.fingerprint());
    }

    @Test
    void forkKeepsLiveCacheIsolatedFromBackgroundRebuild() {
        SceneTileSnapshot first = terrainTile(1, 1, 100);
        SceneTileSnapshot second = terrainTile(16, 1, 200);
        IncrementalGpuUploadPlanBuilder live = new IncrementalGpuUploadPlanBuilder();
        live.buildInitial(packet(List.of(first, second), "initial"));

        IncrementalGpuUploadPlanBuilder background = live.fork();
        SceneTileSnapshot changedSecond = terrainTile(16, 1, 300);
        background.build(packet(List.of(first, changedSecond), "background"),
                Set.of(WorldZoneCoordinate.from(changedSecond.worldAddress())));

        assertEquals(2, live.cachedTileCount());
        var liveReuse = live.build(packet(List.of(first, second), "live"), Set.of());
        assertEquals(0, liveReuse.rebuiltTiles());
        assertEquals(2, liveReuse.reusedTiles());
    }

    @Test
    void cachePrunesTilesRemovedByVisibilityProjection() {
        SceneTileSnapshot first = terrainTile(1, 1, 100);
        SceneTileSnapshot second = terrainTile(2, 1, 100);
        IncrementalGpuUploadPlanBuilder incremental = new IncrementalGpuUploadPlanBuilder();

        incremental.buildInitial(packet(List.of(first, second), "all"));
        incremental.build(packet(List.of(first), "filtered"), Set.of());

        assertEquals(1, incremental.cachedTileCount());
    }

    private static SceneTileSnapshot objectTile(int worldX, int worldY, int objectId) {
        WorldTileAddress address = WorldTileAddress.of(worldX, worldY, 0);
        TileCoordinate coordinate = new TileCoordinate(0, worldX, worldY);
        SceneObjectIdentity identity = SceneObjectIdentity.of(
                new WorldObject(objectId, 10, 0, 0, worldX, worldY), 1, 1);
        ModelRenderPacket model = new ModelRenderPacket(coordinate, objectId, ObjectCategory.GROUND,
                List.of(new ModelVertex(44, -20, 36, 0, 0, 0, 1, 0, 0),
                        new ModelVertex(84, -20, 36, 0, 0, 0, 1, 0, 0),
                        new ModelVertex(64, 20, 36, 0, 0, 0, 1, 0, 0)),
                List.of(new ModelTriangle(0, 1, 2, 100, 100, 100,
                        -1, 0, 0, 0)), List.of(), -1,
                44, -20, 36, 84, 20, 36, false, false)
                .withClientModelBounds(ClientModelBounds.calculate(
                        List.of(new ModelVertex(-20, -20, 36, 0, 0, 0, 0, 0, 0),
                                new ModelVertex(20, -20, 36, 0, 0, 0, 0, 0, 0),
                                new ModelVertex(0, 20, 36, 0, 0, 0, 0, 0, 0)),
                        0, false))
                .withSceneObjectIdentity(identity);
        return new SceneTileSnapshot(
                coordinate, address, 0, 0, 0, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
    }

    private static SceneTileSnapshot terrainTile(int worldX, int worldY, int hsl) {
        WorldTileAddress address = WorldTileAddress.of(worldX, worldY, 0);
        TileCoordinate coordinate = new TileCoordinate(0, worldX, worldY);
        TerrainRenderPacket terrain = new TerrainRenderPacket(
                coordinate,
                List.of(new TerrainRenderVertex(0, 0, 0, hsl, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, hsl, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, hsl, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, hsl, -1, false, false, -1);
        return new SceneTileSnapshot(
                coordinate, address, 0, 0, 0, 0, 0,
                Optional.empty(), Optional.of(terrain), List.of(),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())),
                List.of(), false, false);
    }

    private static GpuScenePacket packet(List<SceneTileSnapshot> tiles, String fingerprint) {
        return new GpuScenePacket(
                new SceneWindow(new WorldRegionWindow(0, 0, 1, 1, Map.of()),
                        0, 0, 1, 0, Set.of(), List.of()),
                tiles, LightingProfile.osrs(), fingerprint, Map.of());
    }
}
