package com.rspsi.editor.simulation;

import com.rspsi.editor.simulation.entity.RuntimeEntity;
import com.rspsi.editor.simulation.state.RuntimeState;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the simulation world state produced at a single point in time,
 * handed downstream to the RuntimeSceneCompiler and viewports.
 */
public record SimulationSnapshot(
        long clientCycle,
        long serverTick,
        float cycleInterpolation,
        float tickInterpolation,
        List<RuntimeEntity> entities,
        RuntimeState state
) {
    public SimulationSnapshot {
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(state, "state");
        entities = Collections.unmodifiableList(List.copyOf(entities));
    }
}
