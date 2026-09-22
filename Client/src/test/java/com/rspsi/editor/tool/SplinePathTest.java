package com.rspsi.editor.tool;

import com.rspsi.editor.tool.spline.SplineBrushStyle;
import com.rspsi.editor.tool.spline.SplinePath;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SplinePathTest {

    @Test
    void pointManagement() {
        SplinePath path = new SplinePath();
        assertTrue(path.isEmpty());
        assertEquals(0, path.size());

        path.addPoint(10, 20, 0);
        path.addPoint(30, 40, 0);
        path.addPoint(50, 60, 0);
        assertEquals(3, path.size());

        assertEquals(new SplinePath.Point(10, 20, 0), path.points().get(0));
        assertEquals(new SplinePath.Point(30, 40, 0), path.points().get(1));

        path.movePoint(1, 35, 45);
        assertEquals(new SplinePath.Point(35, 45, 0), path.points().get(1));

        int near = path.findPointNear(34, 46, 0, 2);
        assertEquals(1, near);

        path.removePoint(0);
        assertEquals(2, path.size());
        assertEquals(new SplinePath.Point(35, 45, 0), path.points().get(0));

        path.clear();
        assertTrue(path.isEmpty());
    }

    @Test
    void evaluateStraightLine() {
        SplinePath path = new SplinePath();
        path.addPoint(0, 0, 0);
        path.addPoint(10, 0, 0);

        float[] samples = path.evaluate(1.0f);
        assertTrue(samples.length >= 4);

        // Check start and end points
        assertEquals(0.0f, samples[0], 0.01f);
        assertEquals(0.0f, samples[1], 0.01f);

        int lastIdx = samples.length - 2;
        assertEquals(10.0f, samples[lastIdx], 0.01f);
        assertEquals(0.0f, samples[lastIdx + 1], 0.01f);
    }

    @Test
    void rasterizeBandProducesContinuousFootprint() {
        SplinePath path = new SplinePath();
        path.addPoint(5, 5, 0);
        path.addPoint(15, 5, 0);
        path.setWidthTiles(1);

        Set<Long> bandWidth1 = path.rasterizeBand(0.5f, 1);
        assertFalse(bandWidth1.isEmpty());
        // For a straight horizontal line of width 1, all y should be 5 and x from 5 to 15
        for (long packed : bandWidth1) {
            int x = SplinePath.unpackX(packed);
            int y = SplinePath.unpackY(packed);
            assertEquals(5, y);
            assertTrue(x >= 5 && x <= 15);
        }

        // Width 3 should cover y=4, y=5, y=6
        Set<Long> bandWidth3 = path.rasterizeBand(0.5f, 3);
        assertTrue(bandWidth3.size() > bandWidth1.size());
        boolean hasY4 = false;
        boolean hasY6 = false;
        for (long packed : bandWidth3) {
            int y = SplinePath.unpackY(packed);
            if (y == 4) hasY4 = true;
            if (y == 6) hasY6 = true;
        }
        assertTrue(hasY4);
        assertTrue(hasY6);
    }

    @Test
    void neighbourMaskAndEdgeStyles() {
        // Build a 3x3 footprint centered at (10, 10)
        Set<Long> footprint = Set.of(
                SplinePath.packCoord(10, 10),
                SplinePath.packCoord(10, 11), // North
                SplinePath.packCoord(11, 10), // East
                SplinePath.packCoord(10, 9),  // South
                SplinePath.packCoord(9, 10)   // West
        );

        // Center tile has all 4 neighbors -> mask 15 (0b1111)
        int centerMask = SplinePath.neighbourMask(footprint, 10, 10);
        assertEquals(15, centerMask);
        assertEquals(0, SplineBrushStyle.SOLID.shape(centerMask)); // Full tile
        assertEquals(0, SplineBrushStyle.WEDGE.shape(centerMask));
        assertEquals(0, SplineBrushStyle.SMOOTH.shape(centerMask));

        // North tile has only South neighbor (10, 10) -> mask 4
        int northMask = SplinePath.neighbourMask(footprint, 10, 11);
        assertEquals(4, northMask);
        assertEquals(1, SplineBrushStyle.SOLID.shape(northMask)); // Straight edge
        assertEquals(6, SplineBrushStyle.WEDGE.shape(northMask)); // Wedge
        assertEquals(11, SplineBrushStyle.SMOOTH.shape(northMask)); // Curved
    }
}
