package com.rspsi.editor.knowledge.derived;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTopologyAnalyzerTest {

    @Test
    void flatTerrainTopology() {
        TerrainTopology topo = TerrainTopology.calculate(100, 100, 100, 100);
        assertEquals(100, topo.minHeight());
        assertEquals(100, topo.maxHeight());
        assertEquals(0, topo.heightVariance());
        assertEquals(0.0, topo.slopeMagnitude());
        assertEquals(TerrainTopology.Aspect.FLAT, topo.aspect());
        assertEquals(TerrainTopology.Curvature.FLAT, topo.curvature());
        assertFalse(topo.isCliff());
    }

    @Test
    void directionalAspectCalculations() {
        // Sloping upwards to the East (SE + NE higher than SW + NW)
        TerrainTopology east = TerrainTopology.calculate(100, 200, 200, 100);
        assertTrue(east.slopeMagnitude() > 0);
        assertEquals(TerrainTopology.Aspect.EAST, east.aspect());

        // Sloping upwards to the North (NE + NW higher than SW + SE)
        TerrainTopology north = TerrainTopology.calculate(100, 100, 200, 200);
        assertTrue(north.slopeMagnitude() > 0);
        assertEquals(TerrainTopology.Aspect.NORTH, north.aspect());

        // Sloping upwards to the West (SW + NW higher than SE + NE)
        TerrainTopology west = TerrainTopology.calculate(200, 100, 100, 200);
        assertTrue(west.slopeMagnitude() > 0);
        assertEquals(TerrainTopology.Aspect.WEST, west.aspect());

        // Sloping upwards to the South (SW + SE higher than NW + NE)
        TerrainTopology south = TerrainTopology.calculate(200, 200, 100, 100);
        assertTrue(south.slopeMagnitude() > 0);
        assertEquals(TerrainTopology.Aspect.SOUTH, south.aspect());
    }

    @Test
    void cliffDetectionThreshold() {
        // Height variance < 120 -> not cliff
        TerrainTopology gentle = TerrainTopology.calculate(0, 0, 100, 100);
        assertEquals(100, gentle.heightVariance());
        assertFalse(gentle.isCliff());

        // Height variance >= 120 -> cliff
        TerrainTopology cliff = TerrainTopology.calculate(0, 0, 140, 140);
        assertEquals(140, cliff.heightVariance());
        assertTrue(cliff.isCliff());
    }

    @Test
    void analyzeWorldDocument() {
        WorldDocument doc = new WorldDocument(4, 4, 1);
        doc.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 200, 200, 0, 0, 0, 0, 0, List.of()));

        Map<TileCoordinate, TerrainTopology> topologyMap = TerrainTopologyAnalyzer.analyze(doc);
        assertEquals(16, topologyMap.size());

        TerrainTopology analyzedTile = topologyMap.get(new TileCoordinate(0, 1, 1));
        assertTrue(analyzedTile.isCliff());
        assertEquals(TerrainTopology.Aspect.NORTH, analyzedTile.aspect());

        TerrainTopology flatTile = topologyMap.get(new TileCoordinate(0, 0, 0));
        assertEquals(TerrainTopology.Aspect.FLAT, flatTile.aspect());
        assertFalse(flatTile.isCliff());
    }
}
