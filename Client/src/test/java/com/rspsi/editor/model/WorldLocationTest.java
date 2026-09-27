package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldLocationTest {
    @Test
    void regionIdGoesToTheRegionCenter() {
        WorldLocation location = WorldLocation.parse("12850");
        assertEquals(WorldLocation.Kind.REGION, location.kind());
        assertEquals(50, location.regionX());
        assertEquals(50, location.regionY());
        assertEquals(12850, location.regionId());
        assertEquals(new WorldTile(0, 3232, 3232), location.tile(0));
    }

    @Test
    void smallPairsAreRegionsAndLargePairsAreWorldTiles() {
        assertEquals(WorldLocation.Kind.REGION, WorldLocation.parse("50,50").kind());
        assertEquals(WorldLocation.Kind.REGION, WorldLocation.parse(" 50 50 ").kind());
        WorldLocation tile = WorldLocation.parse("3222, 3218");
        assertEquals(WorldLocation.Kind.TILE, tile.kind());
        assertEquals(-1, tile.plane());
        assertEquals(new WorldTile(2, 3222, 3218), tile.tile(2));
        assertEquals(12850, tile.regionId());
    }

    @Test
    void aThirdNumberIsThePlane() {
        WorldLocation location = WorldLocation.parse("3222 3218 1");
        assertEquals(new WorldTile(1, 3222, 3218), location.tile(0));
        assertEquals(2, WorldLocation.parse("50,50,2").plane());
    }

    @Test
    void developerTeleportFormatIsPlaneRegionAndLocalTile() {
        WorldLocation location = WorldLocation.parse("0_50_50_22_18");
        assertEquals(new WorldTile(0, 3222, 3218), location.tile(3));
        assertEquals(location, WorldLocation.parse("0,50,50,22,18"));
    }

    @Test
    void explicitFactoriesValidateTheirFields() {
        assertEquals(WorldLocation.parse("3222,3218,1"), WorldLocation.ofTile(3222, 3218, 1));
        assertEquals(WorldLocation.parse("12850"), WorldLocation.ofRegionId(12850));
        assertNull(WorldLocation.ofTile(3222, 3218, 4));
        assertNull(WorldLocation.ofTile(-1, 3218, 0));
        assertNull(WorldLocation.ofRegionId(65536));
    }

    @Test
    void rejectsTextThatIsNotALocation() {
        assertNull(WorldLocation.parse(""));
        assertNull(WorldLocation.parse("lumbridge"));
        assertNull(WorldLocation.parse("70000"));
        assertNull(WorldLocation.parse("3222,3218,4"));
        assertNull(WorldLocation.parse("20000,3218"));
        assertNull(WorldLocation.parse("0,50,50,64,0"));
        assertNull(WorldLocation.parse("1,2,3,4"));
    }
}
