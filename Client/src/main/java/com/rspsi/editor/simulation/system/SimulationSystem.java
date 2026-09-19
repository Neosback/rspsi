package com.rspsi.editor.simulation.system;

import com.rspsi.editor.simulation.clock.SimulationClock;
import com.rspsi.editor.simulation.entity.EntityWorld;
import com.rspsi.editor.simulation.state.RuntimeState;

/**
 * Modular update system operating on EntityWorld and RuntimeState during simulation updates.
 */
public interface SimulationSystem {

    String name();

    /**
     * Executes the system update.
     *
     * @param step the cycles and ticks advanced in this update step
     * @param clock the full clock state
     * @param entities the mutable entity world
     * @param state the current runtime state
     */
    void update(SimulationClock.StepResult step, SimulationClock clock, EntityWorld entities, RuntimeState state);
}
