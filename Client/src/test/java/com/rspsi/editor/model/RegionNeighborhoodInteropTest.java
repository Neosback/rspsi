package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegionNeighborhoodInteropTest {

    @Test
    void constructorKeepsOnlyThreeByThreeNeighborhoodAndCenterWins() {
        WorldRegion center = region(50, 60, 2);
        WorldRegion staleCenter = region(50, 60, 1);
        WorldRegion adjacent = region(51, 61, 2);
        WorldRegion farAway = region(55, 60, 2);

        Map<Integer, WorldRegion> loaded = new LinkedHashMap<>();
        loaded.put(staleCenter.regionId(), staleCenter);
        loaded.put(adjacent.regionId(), adjacent);
        loaded.put(farAway.regionId(), farAway);

        RegionNeighborhood neighborhood = new RegionNeighborhood(center, loaded);

        assertEquals(50, neighborhood.centerRegionX());
        assertEquals(60, neighborhood.centerRegionY());
        assertSame(center, neighborhood.center());
        assertSame(center, neighborhood.region(50, 60).orElseThrow());
        assertSame(adjacent, neighborhood.region(51, 61).orElseThrow());
        assertTrue(neighborhood.region(55, 60).isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> neighborhood.regions().put(farAway.regionId(), farAway));
    }

    @Test
    void preservesExplicitConstructorNullFailures() {
        WorldRegion center = region(50, 60, 1);

        NullPointerException centerFailure = assertThrows(
                NullPointerException.class,
                () -> new RegionNeighborhood(null, Map.of()));
        assertEquals("center", centerFailure.getMessage());

        NullPointerException regionsFailure = assertThrows(
                NullPointerException.class,
                () -> new RegionNeighborhood(center, null));
        assertEquals("loadedRegions", regionsFailure.getMessage());
    }

    @Test
    void fromRemainsTrueJavaStaticFactoryAndRejectsMissingCenter() throws Exception {
        assertTrue(Modifier.isStatic(
                RegionNeighborhood.class
                        .getMethod("from", WorldRegionWindow.class, int.class, int.class)
                        .getModifiers()));

        WorldRegion center = region(10, 20, 1);
        WorldRegionWindow window = new WorldRegionWindow(
                10, 20, 1, 1, Map.of(center.regionId(), center));

        RegionNeighborhood neighborhood = RegionNeighborhood.from(window, 10, 20);
        assertSame(center, neighborhood.center());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RegionNeighborhood.from(window, 11, 20));
        assertEquals("Center region is not loaded: 11,20", failure.getMessage());

        NullPointerException nullWindow = assertThrows(
                NullPointerException.class,
                () -> RegionNeighborhood.from(null, 10, 20));
        assertEquals("window", nullWindow.getMessage());
    }

    @Test
    void worldTileLookupsPreserveAbsenceAndLocalCoordinateMapping() {
        WorldRegion center = region(10, 20, 2);
        RegionNeighborhood neighborhood =
                new RegionNeighborhood(center, Map.of(center.regionId(), center));

        int worldX = 10 * WorldRegion.REGION_SIZE + 5;
        int worldY = 20 * WorldRegion.REGION_SIZE + 7;

        Tile expected = center.document().tile(1, 5, 7);
        assertSame(expected, neighborhood.mutableTileAt(1, worldX, worldY).orElseThrow());
        assertEquals(expected.snapshot(),
                neighborhood.tileAt(1, worldX, worldY).orElseThrow());
        assertEquals(expected.heightSource(),
                neighborhood.tileSource(1, worldX, worldY).orElseThrow().heightSource());

        assertTrue(neighborhood.regionAtWorldTile(-1, worldY).isEmpty());
        assertTrue(neighborhood.tileAt(-1, worldX, worldY).isEmpty());
        assertTrue(neighborhood.tileAt(2, worldX, worldY).isEmpty());
        assertTrue(neighborhood.tileAt(0, 0, 0).isEmpty());
    }

    @Test
    void effectivePlaneAndCenterBoundsPreserveWorldSemantics() {
        WorldRegion center = region(10, 20, 2);
        int worldX = 10 * WorldRegion.REGION_SIZE + 5;
        int worldY = 20 * WorldRegion.REGION_SIZE + 7;

        center.document().tile(1, 5, 7).restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0,
                OsrsTileFlags.BRIDGE,
                java.util.List.of()));

        RegionNeighborhood neighborhood =
                new RegionNeighborhood(center, Map.of(center.regionId(), center));

        assertEquals(0, neighborhood.effectivePlane(1, worldX, worldY));
        assertEquals(-1, neighborhood.effectivePlane(0, worldX, worldY));
        assertThrows(IllegalArgumentException.class,
                () -> neighborhood.effectivePlane(-1, worldX, worldY));

        assertEquals(640, neighborhood.centerOriginX());
        assertEquals(1280, neighborhood.centerOriginY());
        assertTrue(neighborhood.isCenterWorldTile(640, 1280));
        assertTrue(neighborhood.isCenterWorldTile(703, 1343));
        assertFalse(neighborhood.isCenterWorldTile(704, 1343));
        assertFalse(neighborhood.isCenterWorldTile(703, 1344));
    }

    private static WorldRegion region(int regionX, int regionY, int planes) {
        return new WorldRegion(
                regionX,
                regionY,
                new WorldDocument(WorldRegion.REGION_SIZE, WorldRegion.REGION_SIZE, planes));
    }
}
