package com.rspsi.editor.terrain.autotile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the OSRS overlay shape and rotation that best reproduces a continuous
 * {@link PathRegion} inside one tile.
 *
 * <p>OSRS has no runtime autotiling: the client draws whatever shape and
 * rotation the map stores ({@code class264.loadTerrain} reads them from the
 * overlay opcode). Smooth roads and shorelines exist because the map data
 * already holds the right shape per tile. This class chooses that shape the
 * way a map editor must: sample the desired area and pick the atlas entry
 * that matches it best.</p>
 *
 * <p>The score is {@code PORTAL_WEIGHT * edge mismatches + interior
 * mismatches}. Edge ("portal") samples sit just inside each of the eight
 * half-edges, so the two tiles on a border sample the area a hair apart on
 * either side of it; weighting them heavily makes each tile open exactly the
 * half-edges the area crosses, and so makes neighbours agree on where the path
 * passes between them, which is what makes a tiled path read as one continuous
 * shape. Interior coverage (8x8) then chooses between shapes with the same
 * edges.</p>
 */
public final class OverlayAutotiler {
    public static final int PORTAL_WEIGHT = 24;

    public enum Kind {
        /** The region misses the tile: leave it unpainted. */
        NONE,
        /** Plain full overlay: map shape 0. */
        FULL,
        /** A partial overlay shape. */
        SHAPE
    }

    /** Fit for one tile. {@code shape}/{@code rotation} are map values (shape 0-11, rotation 0-3). */
    public record TileFit(int tileX, int tileY, Kind kind, int shape, int rotation, int score,
                          float targetCoverage) {
        public boolean painted() {
            return kind != Kind.NONE;
        }
    }

    private record Candidate(Kind kind, int shape, int rotation, long coverage, int portals) {
    }

    private static final List<Candidate> CANDIDATES = new ArrayList<>();

    static {
        CANDIDATES.add(new Candidate(Kind.NONE, 0, 0, 0L, 0));
        CANDIDATES.add(new Candidate(Kind.FULL, 0, 0, OverlayShapeAtlas.FULL_COVERAGE, OverlayShapeAtlas.ALL_PORTALS));
        for (int shape = 1; shape < OverlayShapeAtlas.SHAPES; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                OverlayShapeAtlas.Entry entry = OverlayShapeAtlas.entry(shape, rotation);
                boolean duplicate = CANDIDATES.stream().anyMatch(c -> c.coverage() == entry.coverage()
                        && c.portals() == entry.portals());
                if (!duplicate) {
                    CANDIDATES.add(new Candidate(Kind.SHAPE, shape, rotation, entry.coverage(), entry.portals()));
                }
            }
        }
    }

    private OverlayAutotiler() {
    }

    /** Shape candidates after removing rotations that produce identical coverage. */
    public static int candidateCount() {
        return CANDIDATES.size();
    }

    public static TileFit fit(PathRegion region, int tileX, int tileY) {
        long coverage = targetCoverage(region, tileX, tileY);
        return fit(tileX, tileY, coverage, targetPortals(region, tileX, tileY));
    }

    /**
     * Fits a set of tiles together. OSRS shapes can draw only 38 of the 256
     * possible open/closed half-edge patterns (a path that just nicks one
     * half-edge, or a tile missing one half-edge, has no shape), so a tile
     * sometimes has to round an edge open or shut. When it does, the neighbour
     * across that edge is refit with the same edge forced, so the two still
     * meet cleanly, provided the neighbour can then draw its own edges exactly
     * (otherwise forcing would spread slivers outward and the single notch is
     * the smaller error). Repeats until nothing changes.
     *
     * @param tiles    tiles to fit, as {@code {x, y}}
     * @param fixedEdges realized portals of tiles outside the set that must be
     *                 matched (e.g. existing path tiles that are not being
     *                 repainted), or {@code null} to ignore outside tiles
     */
    public static Map<Long, TileFit> fitTiles(PathRegion region, Collection<int[]> tiles, PortalLookup fixedEdges) {
        Map<Long, long[]> targets = new LinkedHashMap<>();
        Map<Long, int[]> forced = new LinkedHashMap<>();
        Map<Long, TileFit> fits = new LinkedHashMap<>();
        for (int[] tile : tiles) {
            long key = key(tile[0], tile[1]);
            targets.put(key, new long[]{targetCoverage(region, tile[0], tile[1]),
                    targetPortals(region, tile[0], tile[1])});
            forced.put(key, new int[]{0, 0});
        }
        for (int pass = 0; pass < 12; pass++) {
            boolean changed = false;
            for (Map.Entry<Long, long[]> target : targets.entrySet()) {
                int x = (int) (target.getKey() >> 32);
                int y = (int) (long) target.getKey();
                int[] force = forced.get(target.getKey());
                int wanted = (((int) target.getValue()[1]) & ~force[0]) | force[1];
                TileFit next = fit(x, y, target.getValue()[0], wanted, force[0]);
                if (!next.equals(fits.get(target.getKey()))) {
                    fits.put(target.getKey(), next);
                    changed = true;
                }
            }
            for (Map.Entry<Long, TileFit> entry : fits.entrySet()) {
                TileFit fit = entry.getValue();
                int realized = portals(fit);
                int intended = (int) targets.get(entry.getKey())[1];
                for (int p = 0; p < 8; p++) {
                    int[] across = ACROSS[p];
                    int nx = fit.tileX() + across[0];
                    int ny = fit.tileY() + across[1];
                    int np = across[2];
                    int mine = (realized >> p) & 1;
                    long neighbourKey = key(nx, ny);
                    TileFit neighbour = fits.get(neighbourKey);
                    if (neighbour == null) {
                        // An outside tile that keeps its shape: this tile must meet it.
                        if (fixedEdges == null) continue;
                        int theirs = fixedEdges.portals(nx, ny);
                        if (theirs < 0) continue;
                        changed |= force(forced.get(entry.getKey()), p, (theirs >> np) & 1);
                        continue;
                    }
                    int theirs = (portals(neighbour) >> np) & 1;
                    if (mine == theirs) continue;
                    // Only the tile that had to round its edge is authoritative for that edge,
                    // and the neighbour follows only if it can then draw all its edges exactly;
                    // otherwise one small notch is better than a cascade of corner slivers.
                    if (mine != ((intended >> p) & 1)) {
                        int[] trial = forced.get(neighbourKey).clone();
                        if (!force(trial, np, mine)) continue;
                        long[] neighbourTarget = targets.get(neighbourKey);
                        int wantedThere = (((int) neighbourTarget[1]) & ~trial[0]) | trial[1];
                        TileFit refit = fit(nx, ny, neighbourTarget[0], wantedThere, trial[0]);
                        if (portals(refit) == wantedThere) {
                            forced.put(neighbourKey, trial);
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) break;
        }
        return fits;
    }

    /** Realized portals of a tile outside a fit: {@code -1} when it has no say. */
    @FunctionalInterface
    public interface PortalLookup {
        int portals(int tileX, int tileY);
    }

    /** Portals the fit actually draws. */
    public static int portals(TileFit fit) {
        return switch (fit.kind()) {
            case NONE -> 0;
            case FULL -> OverlayShapeAtlas.ALL_PORTALS;
            case SHAPE -> OverlayShapeAtlas.entry(fit.shape(), fit.rotation()).portals();
        };
    }

    /** For portal p: the neighbour offset and the neighbour's portal on the same half-edge. */
    private static final int[][] ACROSS = {
            {0, -1, 5}, {0, -1, 4}, {1, 0, 7}, {1, 0, 6},
            {0, 1, 1}, {0, 1, 0}, {-1, 0, 3}, {-1, 0, 2}};

    private static boolean force(int[] force, int portal, int value) {
        int bit = 1 << portal;
        boolean already = (force[0] & bit) != 0 && ((force[1] & bit) != 0) == (value == 1);
        if (already) return false;
        force[0] |= bit;
        if (value == 1) force[1] |= bit;
        else force[1] &= ~bit;
        return true;
    }

    private static long targetCoverage(PathRegion region, int tileX, int tileY) {
        long coverage = 0L;
        for (int j = 0; j < OverlayShapeAtlas.RESOLUTION; j++) {
            for (int i = 0; i < OverlayShapeAtlas.RESOLUTION; i++) {
                if (region.contains(tileX + OverlayShapeAtlas.sampleX(i), tileY + OverlayShapeAtlas.sampleY(j))) {
                    coverage |= 1L << OverlayShapeAtlas.bit(i, j);
                }
            }
        }
        return coverage;
    }

    private static int targetPortals(PathRegion region, int tileX, int tileY) {
        int portals = 0;
        for (int p = 0; p < 8; p++) {
            double[] point = OverlayShapeAtlas.portalSample(p);
            if (region.contains(tileX + point[0], tileY + point[1])) portals |= 1 << p;
        }
        return portals;
    }

    /** Best candidate for a target coverage mask and edge signature. */
    public static TileFit fit(int tileX, int tileY, long coverage, int portals) {
        return fit(tileX, tileY, coverage, portals, 0);
    }

    /** As {@link #fit(int, int, long, int)}, with {@code forcedMask} edges that must match exactly. */
    private static TileFit fit(int tileX, int tileY, long coverage, int portals, int forcedMask) {
        Candidate best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Candidate candidate : CANDIDATES) {
            int edgeMismatch = candidate.portals() ^ portals;
            int score = PORTAL_WEIGHT * Integer.bitCount(edgeMismatch)
                    + PORTAL_WEIGHT * 64 * Integer.bitCount(edgeMismatch & forcedMask)
                    + Long.bitCount(candidate.coverage() ^ coverage);
            // Candidates are ordered NONE, FULL, then shapes: strict '<' keeps the simplest on ties.
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        float target = Long.bitCount(coverage) / (float) (OverlayShapeAtlas.RESOLUTION * OverlayShapeAtlas.RESOLUTION);
        return new TileFit(tileX, tileY, best.kind(), best.shape(), best.rotation(), bestScore, target);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
