package com.rspsi.editor.simulation;

import com.rspsi.editor.simulation.clock.SimulationClock;
import com.rspsi.editor.simulation.entity.EntityWorld;
import com.rspsi.editor.simulation.entity.RuntimeEntity;
import com.rspsi.editor.simulation.state.ManualStateProvider;
import com.rspsi.editor.simulation.state.RuntimeState;
import com.rspsi.editor.simulation.state.RuntimeStateProvider;
import com.rspsi.editor.simulation.system.MovementSystem;
import com.rspsi.editor.simulation.system.SequenceSystem;
import com.rspsi.editor.simulation.system.SimulationSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative core Studio simulation engine coordinating the multi-domain clock,
 * entity world, modular simulation systems, and runtime state.
 */
public final class SimulationEngine {
    private final SimulationClock clock = new SimulationClock();
    private final EntityWorld entities = new EntityWorld();
    private final List<SimulationSystem> systems = new CopyOnWriteArrayList<>();
    private RuntimeStateProvider stateProvider;

    public SimulationEngine() {
        this(new ManualStateProvider());
    }

    public SimulationEngine(RuntimeStateProvider stateProvider) {
        this.stateProvider = Objects.requireNonNull(stateProvider, "stateProvider");
        // Install default modular systems
        registerSystem(new SequenceSystem());
        registerSystem(new MovementSystem());
    }

    public SimulationClock clock() {
        return clock;
    }

    public EntityWorld entities() {
        return entities;
    }

    public RuntimeState state() {
        return stateProvider.state();
    }

    public RuntimeStateProvider stateProvider() {
        return stateProvider;
    }

    public void setStateProvider(RuntimeStateProvider provider) {
        this.stateProvider = Objects.requireNonNull(provider, "provider");
    }

    public void registerSystem(SimulationSystem system) {
        Objects.requireNonNull(system, "system");
        systems.add(system);
    }

    public void unregisterSystem(String name) {
        systems.removeIf(s -> s.name().equals(name));
    }

    public List<SimulationSystem> systems() {
        return Collections.unmodifiableList(systems);
    }

    /**
     * Updates the simulation by the given elapsed nanoseconds.
     * Executes modular systems if client cycles or server ticks elapsed.
     */
    public SimulationSnapshot update(long deltaNanos) {
        SimulationClock.StepResult step = clock.advance(deltaNanos);
        if (step.hasAdvanced()) {
            RuntimeState currentState = state();
            for (SimulationSystem system : systems) {
                system.update(step, clock, entities, currentState);
            }
        }
        return snapshot();
    }

    /** Single-steps exactly 1 client cycle when paused. */
    public SimulationSnapshot stepClientCycle() {
        SimulationClock.StepResult step = clock.stepClientCycle();
        RuntimeState currentState = state();
        for (SimulationSystem system : systems) {
            system.update(step, clock, entities, currentState);
        }
        return snapshot();
    }

    /** Single-steps exactly 1 server tick (30 client cycles) when paused. */
    public SimulationSnapshot stepServerTick() {
        SimulationClock.StepResult step = clock.stepServerTick();
        RuntimeState currentState = state();
        for (SimulationSystem system : systems) {
            system.update(step, clock, entities, currentState);
        }
        return snapshot();
    }

    /** Captures an immutable snapshot of the active simulation world. */
    public SimulationSnapshot snapshot() {
        return new SimulationSnapshot(
                clock.clientCycles(),
                clock.serverTicks(),
                clock.cycleInterpolation(),
                clock.tickInterpolation(),
                new ArrayList<>(entities.all()),
                state()
        );
    }

    public void reset() {
        clock.reset();
        entities.clear();
        stateProvider.reset();
    }
}
