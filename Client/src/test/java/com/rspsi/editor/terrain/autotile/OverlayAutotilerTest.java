package com.rspsi.editor.terrain.autotile;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import com.rspsi.editor.terrain.TerrainVertex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayAutotilerTest {
    @Test
    void atlasMatchesTheSceneTerrainMeshForEveryShapeAndRotation() {
        for (int shape = 0; shape < OverlayShapeAtlas.SHAPES; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TerrainMesh mesh = new TerrainMeshBuilder().build(
                        new TileSnapshot(0, 0, 0, 0, 1, 1, shape, rotation, 0, List.of()));
                long meshCoverage = 0L;
                for (int j = 0; j < OverlayShapeAtlas.RESOLUTION; j++) {
                    for (int i = 0; i < OverlayShapeAtlas.RESOLUTION; i++) {
                        if (overlayMeshCovers(mesh, OverlayShapeAtlas.sampleX(i), OverlayShapeAtlas.sampleY(j))) {
                            meshCoverage |= 1L << OverlayShapeAtlas.bit(i, j);
                        }
                    }
                }
                assertEquals(meshCoverage, OverlayShapeAtlas.entry(shape, rotation).coverage(),
                        "shape " + shape + " rotation " + rotation);
            }
        }
    }

    @Test
    void straightBandAlignedToTheGridPaintsFullTilesOnly() {
        // Three tiles wide, centred on row 5: rows 4-6 are inside, edges on tile borders.
        PathRegion band = new StrokeRegion(List.of(new double[]{0.0, 5.5}, new double[]{20.0, 5.5}), 1.5);

        for (int y = 3; y <= 7; y++) {
            OverlayAutotiler.TileFit fit = OverlayAutotiler.fit(band, 10, y);
            OverlayAutotiler.Kind expected = y >= 4 && y <= 6 ? OverlayAutotiler.Kind.FULL : OverlayAutotiler.Kind.NONE;
            assertEquals(expected, fit.kind(), "row " + y);
        }
    }

    @Test
    void diagonalEdgePicksTheDiagonalHalfShape() {
        // Everything south-west of the line through the tile's NW and SE corners.
        PathRegion halfPlane = (x, y) -> (x - 10) + (y - 20) < 1.0 - 1e-9;

        OverlayAutotiler.TileFit fit = OverlayAutotiler.fit(halfPlane, 10, 20);

        assertEquals(OverlayAutotiler.Kind.SHAPE, fit.kind());
        assertEquals(OverlayShapeAtlas.entry(1, 0).coverage(),
                OverlayShapeAtlas.entry(fit.shape(), fit.rotation()).coverage());
    }

    @Test
    void curvedStrokeHasNoGapsBetweenNeighbouringTiles() {
        List<double[]> arc = new ArrayList<>();
        for (int step = 0; step <= 60; step++) {
            double angle = Math.PI * step / 60.0;
            arc.add(new double[]{50 + 12 * Math.cos(angle), 50 + 12 * Math.sin(angle)});
        }
        StrokeRegion stroke = new StrokeRegion(arc, 1.6);
        int[] bounds = stroke.tileBounds();
        List<int[]> tiles = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[2]; x++) {
            for (int y = bounds[1]; y <= bounds[3]; y++) tiles.add(new int[]{x, y});
        }
        Map<Long, OverlayAutotiler.TileFit> fits = OverlayAutotiler.fitTiles(stroke, tiles, null);

        int painted = 0;
        int roundedEdges = 0;
        int crossings = 0;
        int gaps = 0;
        for (OverlayAutotiler.TileFit fit : fits.values()) {
            if (fit.painted()) painted++;
            int realized = OverlayAutotiler.portals(fit);
            int target = targetPortals(stroke, fit.tileX(), fit.tileY());
            roundedEdges += Integer.bitCount(realized ^ target);
            for (int p = 0; p < 8; p++) {
                int[] across = ACROSS[p];
                OverlayAutotiler.TileFit neighbour = fits.get(key(fit.tileX() + across[0], fit.tileY() + across[1]));
                if (neighbour == null) continue;
                int neighbourTarget = targetPortals(stroke, neighbour.tileX(), neighbour.tileY());
                boolean pathCrosses = bit(target, p) == 1 && bit(neighbourTarget, across[2]) == 1;
                if (!pathCrosses) continue;
                crossings++;
                // Where the path crosses a half-edge, the two tiles must draw it the same way;
                // one open and one shut is a visible notch.
                if (bit(realized, p) != bit(OverlayAutotiler.portals(neighbour), across[2])) gaps++;
            }
        }
        assertTrue(crossings > 300, "crossings " + crossings);
        assertEquals(0, gaps, "notches where the path crosses a tile border");
        // OSRS shapes cannot draw a path that only nicks one half-edge; only those few edges round.
        assertTrue(roundedEdges <= painted / 10, "rounded " + roundedEdges + " of " + painted + " tiles");
    }

    /** For portal p: neighbour offset and the neighbour's portal on the same half-edge. */
    private static final int[][] ACROSS = {
            {0, -1, 5}, {0, -1, 4}, {1, 0, 7}, {1, 0, 6},
            {0, 1, 1}, {0, 1, 0}, {-1, 0, 3}, {-1, 0, 2}};

    private static int targetPortals(PathRegion region, int x, int y) {
        int result = 0;
        for (int p = 0; p < 8; p++) {
            double[] point = OverlayShapeAtlas.portalSample(p);
            if (region.contains(x + point[0], y + point[1])) result |= 1 << p;
        }
        return result;
    }

    @Test
    void existingPathTilesExtendSeamlessly() {
        // An existing full-tile path ends at column 9; a new stroke continues east from it.
        OverlayTileRegion existing = new OverlayTileRegion((x, y) ->
                x >= 0 && x <= 9 && y >= 4 && y <= 6 ? OverlayShapeAtlas.entry(0, 0) : null);
        PathRegion union = existing.or(new StrokeRegion(
                List.of(new double[]{9.5, 5.5}, new double[]{20.0, 5.5}), 1.5));

        assertEquals(OverlayAutotiler.Kind.FULL, OverlayAutotiler.fit(union, 9, 4).kind());
        assertEquals(OverlayAutotiler.Kind.FULL, OverlayAutotiler.fit(union, 10, 6).kind());
        assertTrue(existing.contains(10.0, 5.0), "a shared border point belongs to the painted neighbour");
        assertTrue(!existing.contains(10.5, 5.0));
    }

    private static int bit(int value, int index) {
        return (value >> index) & 1;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static boolean overlayMeshCovers(TerrainMesh mesh, double x, double y) {
        for (TerrainFace face : mesh.faces()) {
            if (face.material() != 1) continue;
            TerrainVertex a = mesh.vertices().get(face.a());
            TerrainVertex b = mesh.vertices().get(face.b());
            TerrainVertex c = mesh.vertices().get(face.c());
            double[] triangle = {a.x() / 128.0, a.y() / 128.0, b.x() / 128.0, b.y() / 128.0, c.x() / 128.0, c.y() / 128.0};
            if (OverlayShapeAtlas.inTriangle(x, y, triangle)) return true;
        }
        return false;
    }
}
