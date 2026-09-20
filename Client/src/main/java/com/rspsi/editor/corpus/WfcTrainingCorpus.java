package com.rspsi.editor.corpus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Statistical tile-state and radius-neighborhood corpus for WFC solvers. */
public record WfcTrainingCorpus(
        int radius,
        Set<Integer> regionIds,
        Map<WfcTileState, Long> frequencies,
        Map<NeighborRule, Long> adjacency) {

    public WfcTrainingCorpus {
        if (radius < 1 || radius > 5) {
            throw new IllegalArgumentException("WFC training radius must be between 1 and 5");
        }
        regionIds = Set.copyOf(regionIds == null ? Set.of() : regionIds);
        frequencies = Map.copyOf(frequencies == null ? Map.of() : frequencies);
        adjacency = Map.copyOf(adjacency == null ? Map.of() : adjacency);
    }

    public Map<WfcTileState, Long> neighbors(WfcTileState source, int dx, int dy) {
        Map<WfcTileState, Long> result = new LinkedHashMap<>();
        adjacency.forEach((rule, count) -> {
            if (rule.source().equals(source) && rule.dx() == dx && rule.dy() == dy) {
                result.merge(rule.neighbor(), count, Long::sum);
            }
        });
        return Map.copyOf(result);
    }

    public record NeighborRule(WfcTileState source, int dx, int dy, WfcTileState neighbor) {
        public NeighborRule {
            if (dx == 0 && dy == 0) {
                throw new IllegalArgumentException("A WFC neighbor offset cannot be zero");
            }
        }
    }
}
