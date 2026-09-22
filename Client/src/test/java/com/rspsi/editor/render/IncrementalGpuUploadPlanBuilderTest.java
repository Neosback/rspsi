package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

        SceneTileSnapshot changedSecond = terrainTile(8, 4, 300);
        GpuScenePacket changed = packet(List.of(first, changedSecond), "changed");
        Set<WorldZoneCoordinate> dirty =
                Set.of(WorldZoneCoordinate.from(changedSecond.worldAddress()));

        var update = incremental.build(changed, dirty);
        GpuUploadPlan expected = new GpuUploadPlanBuilder().build(changed);

        assertEquals(1, update.rebuiltTiles());
        assertEquals(1, update.reusedTiles());
        assertEquals(expected.vertices(), update.plan().vertices());
        assertEquals(expected.indices(), update.plan().indices());
        assertEquals(expected.commands(), update.plan().commands());
        assertEquals(expected.textureTriangles(), update.plan().textureTriangles());
        assertEquals(expected.occluders(), update.plan().occluders());
        assertEquals(expected.fingerprint(), update.plan().fingerprint());
        assertNotEquals(seeded.plan().fingerprint(), update.plan().fingerprint());
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
