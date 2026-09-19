package com.rspsi.editor.simulation.system;

import com.rspsi.editor.simulation.clock.SimulationClock;
import com.rspsi.editor.simulation.entity.EntityWorld;
import com.rspsi.editor.simulation.entity.RuntimeEntity;
import com.rspsi.editor.simulation.state.RuntimeState;

import java.util.ArrayList;
import java.util.List;

/**
 * System that advances sequence animation frames across entities on 50Hz client cycle updates.
 */
public final class SequenceSystem implements SimulationSystem {

    @Override
    public String name() {
        return "SequenceSystem";
    }

    @Override
    public void update(SimulationClock.StepResult step, SimulationClock clock, EntityWorld entities, RuntimeState state) {
        if (step.clientCyclesAdvanced() <= 0) return;

        List<RuntimeEntity> snapshot = new ArrayList<>(entities.all());
        for (RuntimeEntity entity : snapshot) {
            if (entity.animationSequence() >= 0) {
                // In standard OSRS animation playback, frames advance periodically.
                // Advance frame index monotonically based on elapsed cycles:
                int nextFrame = entity.animationFrame() + step.clientCyclesAdvanced();
                RuntimeEntity updated = entity.withAnimation(entity.animationSequence(), nextFrame);
                entities.add(updated);
            }
        }
    }
}
