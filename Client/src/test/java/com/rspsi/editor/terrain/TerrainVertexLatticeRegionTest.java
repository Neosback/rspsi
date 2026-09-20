package com.rspsi.editor.terrain;

import com.rspsi.editor.model.RegionNeighborhood;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerrainVertexLatticeRegionTest {
    @Test
    void worldVertexAtRegionBorderUpdatesBothLoadedRegions() {
        WorldRegion west = new WorldRegion(20, 30, new WorldDocument(64, 64, 1));
        WorldRegion east = new WorldRegion(21, 30, new WorldDocument(64, 64, 1));
        RegionNeighborhood neighborhood = new RegionNeighborhood(west,
                Map.of(west.regionId(), west, east.regionId(), east));
        TerrainVertexLattice lattice = new TerrainVertexLattice(neighborhood);

        int borderX = east.regionX() * 64;
        int worldY = west.regionY() * 64 + 10;
        var changed = lattice.setHeightWorld(0, borderX, worldY, 144);

        assertEquals(144, west.document().tile(0, 63, 10).snapshot().southEastHeight());
        assertEquals(144, east.document().tile(0, 0, 10).snapshot().southWestHeight());
        assertEquals(144, lattice.heightWorld(0, borderX, worldY));
        assertTrue(changed.stream().anyMatch(address -> address.regionId() == west.regionId()));
        assertTrue(changed.stream().anyMatch(address -> address.regionId() == east.regionId()));
    }
}
