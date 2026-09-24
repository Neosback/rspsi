package com.rspsi.editor.paste;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentPastePlannerTest {
    private static final int REGION_X = 50;
    private static final int REGION_Y = 50;
    private static final int WEST_ID = (REGION_X << 8) | REGION_Y;
    private static final int EAST_ID = ((REGION_X + 1) << 8) | REGION_Y;
    private static final int WORLD_Y = REGION_Y * 64 + 10;

    @Test
    void replaceAllPlansAndCommitsTerrainAndLocalizedObjectsAcrossRegions() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 63;
        WorldFragment fragment = twoTileFragment();

        WorldFragmentPasteResult result = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y,
                WorldFragmentPastePolicy.replaceAll());

        assertTrue(result.canCommit());
        assertFalse(result.partial());
        assertEquals(Set.of(WEST_ID, EAST_ID),
                result.candidatePlan().affectedRegionIds());

        WorldTile eastWorld = new WorldTile(0, targetX + 1, WORLD_Y);
        TileSnapshot eastAfter = result.candidatePlan()
                .tileChanges().get(eastWorld).after();
        assertEquals(8, eastAfter.underlayId());
        assertEquals(List.of(new WorldObject(
                        100, 10, 2, 0, 0, 10)),
                eastAfter.objects(),
                "world-space object anchor must be localized into east region payload");

        assertTrue(fixture.window().commit(result.requireCommittablePlan()));
        assertEquals(7, fixture.west().tile(0, 63, 10).snapshot().underlayId());
        assertEquals(8, fixture.east().tile(0, 0, 10).snapshot().underlayId());
        assertEquals(List.of(new WorldObject(100, 10, 2, 0, 0, 10)),
                fixture.east().tile(0, 0, 10).snapshot().objects());
        assertEquals(1, fixture.window().changeHistory().size());

        assertTrue(fixture.window().undoChangePlan());
        assertEquals(0, fixture.west().tile(0, 63, 10).snapshot().underlayId());
        assertEquals(0, fixture.east().tile(0, 0, 10).snapshot().underlayId());
        assertTrue(fixture.east().tile(0, 0, 10).snapshot().objects().isEmpty());
    }

    @Test
    void terrainOnlyPreservesDestinationObjects() {
        Fixture fixture = twoRegionFixture();
        WorldObject existing = new WorldObject(55, 0, 1, 0, 5, 10);
        fixture.west().tile(0, 5, 10).restore(snapshot(
                1, 2, 3, 4, 3, List.of(existing)));

        WorldFragment fragment = oneTileFragment(
                snapshot(10, 20, 30, 40, 77, List.of()));
        int targetX = REGION_X * 64 + 5;

        WorldFragmentPasteResult result = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y,
                WorldFragmentPastePolicy.terrainOnly());

        TileSnapshot after = result.requireCommittablePlan().tileChanges()
                .get(new WorldTile(0, targetX, WORLD_Y)).after();
        assertEquals(77, after.underlayId());
        assertEquals(List.of(existing), after.objects());
    }

    @Test
    void objectsOnlyPreservesDestinationTerrainAndReplacesObjectLayer() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 6;
        TileSnapshot destination = snapshot(
                101, 102, 103, 104, 33,
                List.of(new WorldObject(1, 22, 0, 0, 6, 10)));
        fixture.west().tile(0, 6, 10).restore(destination);

        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(new TerrainTilePatch(0, 0, 0,
                        snapshot(1, 2, 3, 4, 99, List.of()))),
                List.of(new WorldObject(2, 10, 3, 0, 0, 0)));

        WorldFragmentPasteResult result = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y,
                WorldFragmentPastePolicy.objectsOnly());

        TileSnapshot after = result.requireCommittablePlan().tileChanges()
                .get(new WorldTile(0, targetX, WORLD_Y)).after();
        assertEquals(33, after.underlayId());
        assertEquals(101, after.southWestHeight());
        assertEquals(104, after.northWestHeight());
        assertEquals(List.of(new WorldObject(2, 10, 3, 0, 6, 10)),
                after.objects());
    }

    @Test
    void mergeObjectsDeduplicatesIdenticalDestinationObject() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 7;
        WorldObject existing = new WorldObject(2, 10, 3, 0, 7, 10);
        WorldObject other = new WorldObject(3, 22, 0, 0, 7, 10);
        fixture.west().tile(0, 7, 10).restore(snapshot(
                0, 0, 0, 0, 5, List.of(existing, other)));

        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(new TerrainTilePatch(0, 0, 0,
                        snapshot(0, 0, 0, 0, 99, List.of()))),
                List.of(new WorldObject(2, 10, 3, 0, 0, 0)));

        TileSnapshot after = WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, targetX, WORLD_Y,
                        WorldFragmentPastePolicy.mergeObjects())
                .requireCommittablePlan()
                .tileChanges().get(new WorldTile(0, targetX, WORLD_Y)).after();

        assertEquals(List.of(existing, other), after.objects());
        assertEquals(5, after.underlayId());
    }

    @Test
    void preserveDestinationHeightsCopiesMaterialsWithoutMovingTerrain() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 8;
        TileSnapshot before = new TileSnapshot(
                100, 101, 102, 103,
                3, 4, 1, 2, 7, List.of(),
                TerrainHeightSource.explicitSource(12));
        fixture.west().tile(0, 8, 10).restore(before, before.heightSource());

        WorldFragment fragment = oneTileFragment(
                snapshot(1, 2, 3, 4, 88, List.of()));
        WorldFragmentPastePolicy policy = WorldFragmentPastePolicy.terrainOnly()
                .withHeightMode(
                        WorldFragmentPastePolicy.HeightMode.PRESERVE_DESTINATION);

        TileSnapshot after = WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, targetX, WORLD_Y, policy)
                .requireCommittablePlan()
                .tileChanges().get(new WorldTile(0, targetX, WORLD_Y)).after();

        assertEquals(88, after.underlayId());
        assertEquals(100, after.southWestHeight());
        assertEquals(101, after.southEastHeight());
        assertEquals(102, after.northEastHeight());
        assertEquals(103, after.northWestHeight());
        assertEquals(before.heightSource(), after.heightSource());
    }

    @Test
    void heightOffsetFromAnchorPreservesRelativeStructureElevation() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 9;
        fixture.west().tile(0, 9, 10).restore(
                snapshot(100, 100, 100, 100, 1, List.of()));

        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 1, 0),
                List.of(
                        new TerrainTilePatch(0, 0, 0,
                                snapshot(10, 20, 30, 40, 7, List.of())),
                        new TerrainTilePatch(0, 1, 0,
                                snapshot(20, 30, 40, 50, 8, List.of()))),
                List.of());
        WorldFragmentPastePolicy policy = WorldFragmentPastePolicy.terrainOnly()
                .withHeightMode(
                        WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR);

        WorldFragmentPasteResult result = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y, policy);

        TileSnapshot first = result.requireCommittablePlan().tileChanges()
                .get(new WorldTile(0, targetX, WORLD_Y)).after();
        TileSnapshot second = result.requireCommittablePlan().tileChanges()
                .get(new WorldTile(0, targetX + 1, WORLD_Y)).after();

        assertEquals(100, first.southWestHeight());
        assertEquals(110, first.southEastHeight());
        assertEquals(120, first.northEastHeight());
        assertEquals(130, first.northWestHeight());
        assertEquals(110, second.southWestHeight());
        assertEquals(140, second.northWestHeight());
        assertTrue(first.heightSource().authored());
        assertTrue(second.heightSource().authored());
    }

    @Test
    void reportConflictsBlocksPartialCommitWhileSkipModeMakesItExplicit() {
        Fixture fixture = westOnlyFixture();
        int targetX = REGION_X * 64 + 63;
        WorldFragment fragment = twoTileFragment();

        WorldFragmentPasteResult report = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y,
                WorldFragmentPastePolicy.replaceAll());

        assertFalse(report.canCommit());
        assertFalse(report.partial());
        assertEquals(1, report.conflicts().size());
        assertEquals(WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                report.conflicts().get(0).code());
        assertThrows(IllegalStateException.class, report::requireCommittablePlan);
        assertEquals(1, report.candidatePlan().tileChanges().size(),
                "resolved portion remains previewable but is not committable");

        WorldFragmentPastePolicy skipPolicy = WorldFragmentPastePolicy.replaceAll()
                .withConflictMode(WorldFragmentPastePolicy.ConflictMode.SKIP);
        WorldFragmentPasteResult skip = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, targetX, WORLD_Y, skipPolicy);

        assertTrue(skip.canCommit());
        assertTrue(skip.partial());
        assertEquals(1, skip.candidatePlan().tileChanges().size());
        assertTrue(fixture.window().commit(skip.requireCommittablePlan()));
        assertEquals(7, fixture.west().tile(0, 63, 10).snapshot().underlayId());
    }

    @Test
    void objectTypeFilterReplacesOnlyMatchingNativeLocTypes() {
        Fixture fixture = twoRegionFixture();
        int targetX = REGION_X * 64 + 11;
        WorldObject wall = new WorldObject(1, 0, 0, 0, 11, 10);
        WorldObject scenery = new WorldObject(2, 10, 1, 0, 11, 10);
        fixture.west().tile(0, 11, 10).restore(
                snapshot(0, 0, 0, 0, 5, List.of(wall, scenery)));

        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(new TerrainTilePatch(0, 0, 0,
                        snapshot(0, 0, 0, 0, 5, List.of()))),
                List.of(new WorldObject(9, 10, 3, 0, 0, 0)));

        WorldFragmentPastePolicy policy = WorldFragmentPastePolicy.objectsOnly()
                .withObjectTypes(Set.of(10));
        TileSnapshot after = WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, targetX, WORLD_Y, policy)
                .requireCommittablePlan()
                .tileChanges().get(new WorldTile(0, targetX, WORLD_Y)).after();

        assertEquals(List.of(
                wall,
                new WorldObject(9, 10, 3, 0, 11, 10)),
                after.objects());
    }

    @Test
    void duplicateSourceTerrainIsReportedDeterministically() {
        Fixture fixture = twoRegionFixture();
        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(
                        new TerrainTilePatch(0, 0, 0,
                                snapshot(0, 0, 0, 0, 1, List.of())),
                        new TerrainTilePatch(0, 0, 0,
                                snapshot(0, 0, 0, 0, 2, List.of()))),
                List.of());

        WorldFragmentPasteResult result = WorldFragmentPastePlanner.plan(
                fixture.window(), fragment, REGION_X * 64, WORLD_Y,
                WorldFragmentPastePolicy.terrainOnly());

        assertFalse(result.canCommit());
        assertEquals(WorldFragmentPasteResult.ConflictCode.DUPLICATE_SOURCE_TILE,
                result.conflicts().get(0).code());
        TileSnapshot candidate = result.candidatePlan().tileChanges()
                .get(new WorldTile(0, REGION_X * 64, WORLD_Y)).after();
        assertEquals(1, candidate.underlayId(),
                "the first source patch wins deterministically for preview");
    }

    private static WorldFragment twoTileFragment() {
        return new WorldFragment(
                new TileBounds(0, 0, 1, 0),
                List.of(
                        new TerrainTilePatch(0, 0, 0,
                                snapshot(1, 2, 3, 4, 7, List.of())),
                        new TerrainTilePatch(0, 1, 0,
                                snapshot(5, 6, 7, 8, 8, List.of()))),
                List.of(new WorldObject(100, 10, 2, 0, 1, 0)));
    }

    private static WorldFragment oneTileFragment(TileSnapshot snapshot) {
        return new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(new TerrainTilePatch(0, 0, 0, snapshot)),
                List.of());
    }

    private static TileSnapshot snapshot(
            int sw, int se, int ne, int nw,
            int underlay,
            List<WorldObject> objects
    ) {
        return new TileSnapshot(
                sw, se, ne, nw,
                underlay, 5, 6, 1, 7,
                objects);
    }

    private static Fixture twoRegionFixture() {
        WorldDocument west = new WorldDocument(64, 64, 2);
        WorldDocument east = new WorldDocument(64, 64, 2);
        WorldRegion westRegion = new WorldRegion(REGION_X, REGION_Y, west);
        WorldRegion eastRegion = new WorldRegion(REGION_X + 1, REGION_Y, east);
        WorldRegionWindow regions = new WorldRegionWindow(
                REGION_X, REGION_Y, 2, 1,
                Map.of(WEST_ID, westRegion, EAST_ID, eastRegion));
        WorldRegionSessionWindow window = new WorldRegionSessionWindow(
                regions,
                Map.of(
                        WEST_ID, new EditorSession(west, westRegion.window()),
                        EAST_ID, new EditorSession(east, eastRegion.window())));
        return new Fixture(window, west, east);
    }

    private static Fixture westOnlyFixture() {
        WorldDocument west = new WorldDocument(64, 64, 2);
        WorldRegion westRegion = new WorldRegion(REGION_X, REGION_Y, west);
        WorldRegionWindow regions = new WorldRegionWindow(
                REGION_X, REGION_Y, 2, 1,
                Map.of(WEST_ID, westRegion));
        WorldRegionSessionWindow window = new WorldRegionSessionWindow(
                regions,
                Map.of(WEST_ID, new EditorSession(west, westRegion.window())));
        return new Fixture(window, west, null);
    }

    private record Fixture(
            WorldRegionSessionWindow window,
            WorldDocument west,
            WorldDocument east
    ) {
    }
}
