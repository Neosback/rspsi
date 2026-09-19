package com.rspsi.editor.simulation.state;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mutable RuntimeStateProvider intended for manual editor inspector controls and test fixtures.
 */
public final class ManualStateProvider implements RuntimeStateProvider {
    private final Map<Integer, Integer> varps = new ConcurrentHashMap<>();
    private final Map<Integer, Object> varcs = new ConcurrentHashMap<>();
    private final Map<TileCoordinate, Integer> temporaryLocStates = new ConcurrentHashMap<>();

    @Override
    public RuntimeState state() {
        return new RuntimeState(varps, varcs, temporaryLocStates);
    }

    @Override
    public void setVarp(int varpId, int value) {
        varps.put(varpId, value);
    }

    @Override
    public void setVarc(int varcId, Object value) {
        if (value == null) {
            varcs.remove(varcId);
        } else {
            varcs.put(varcId, value);
        }
    }

    @Override
    public void setLocState(TileCoordinate coordinate, int state) {
        Objects.requireNonNull(coordinate, "coordinate");
        temporaryLocStates.put(coordinate, state);
    }

    @Override
    public void reset() {
        varps.clear();
        varcs.clear();
        temporaryLocStates.clear();
    }
}
