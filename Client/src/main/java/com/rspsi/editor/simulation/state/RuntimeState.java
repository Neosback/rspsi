package com.rspsi.editor.simulation.state;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of authoritative runtime client/server state (varps, varbits, varcs, inventories, stats).
 */
public record RuntimeState(
        Map<Integer, Integer> varps,
        Map<Integer, Object> varcs,
        Map<TileCoordinate, Integer> temporaryLocStates
) {
    public static final RuntimeState EMPTY = new RuntimeState(Map.of(), Map.of(), Map.of());

    public RuntimeState {
        varps = Collections.unmodifiableMap(Map.copyOf(varps == null ? Map.of() : varps));
        varcs = Collections.unmodifiableMap(Map.copyOf(varcs == null ? Map.of() : varcs));
        temporaryLocStates = Collections.unmodifiableMap(Map.copyOf(temporaryLocStates == null ? Map.of() : temporaryLocStates));
    }

    public int varp(int varpId) {
        return varps.getOrDefault(varpId, 0);
    }

    public Object varc(int varcId) {
        return varcs.get(varcId);
    }

    public int locState(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return temporaryLocStates.getOrDefault(coordinate, 0);
    }

    /**
     * Unpacks a varbit value given the parent varp and the bitmask bounds.
     */
    public int unpackVarbit(int varpId, int leastSignificantBit, int mostSignificantBit) {
        int val = varp(varpId);
        int bitCount = mostSignificantBit - leastSignificantBit + 1;
        int mask = (1 << bitCount) - 1;
        return (val >>> leastSignificantBit) & mask;
    }
}
