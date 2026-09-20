package com.rspsi.editor.corpus;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Trains radius-aware WFC adjacency statistics directly from real cache regions. */
public final class WfcCorpusBuilder {
    public WfcTrainingCorpus build(List<WorldRegion> regions) {
        return build(regions, 5);
    }

    public WfcTrainingCorpus build(List<WorldRegion> regions, int radius) {
        Objects.requireNonNull(regions, "regions");
        if (radius < 1 || radius > 5) {
            throw new IllegalArgumentException("WFC training radius must be between 1 and 5");
        }
        Map<WfcTileState, Long> frequencies = new LinkedHashMap<>();
        Map<WfcTrainingCorpus.NeighborRule, Long> adjacency = new LinkedHashMap<>();
        Set<Integer> regionIds = new LinkedHashSet<>();

        for (WorldRegion region : regions) {
            if (region == null) continue;
            regionIds.add(region.regionId());
            WorldDocument world = region.document();
            for (int plane = 0; plane < world.planes(); plane++) {
                for (int x = 0; x < world.width(); x++) {
                    for (int y = 0; y < world.length(); y++) {
                        WfcTileState source = WfcTileState.from(
                                world.tile(plane, x, y).snapshot());
                        frequencies.merge(source, 1L, Long::sum);
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dy = -radius; dy <= radius; dy++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = x + dx;
                                int ny = y + dy;
                                if (!world.contains(plane, nx, ny)) continue;
                                WfcTileState neighbor = WfcTileState.from(
                                        world.tile(plane, nx, ny).snapshot());
                                adjacency.merge(new WfcTrainingCorpus.NeighborRule(
                                        source, dx, dy, neighbor), 1L, Long::sum);
                            }
                        }
                    }
                }
            }
        }
        return new WfcTrainingCorpus(radius, regionIds, frequencies, adjacency);
    }
}
