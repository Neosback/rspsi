package com.rspsi.editor.simulation.system;

import com.rspsi.editor.simulation.clock.SimulationClock;
import com.rspsi.editor.simulation.entity.EntityWorld;
import com.rspsi.editor.simulation.entity.NpcEntity;
import com.rspsi.editor.simulation.entity.RuntimeEntity;
import com.rspsi.editor.simulation.state.RuntimeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * System that manages entity movement decisions on ~600ms server ticks and step interpolation.
 */
public final class MovementSystem implements SimulationSystem {
    private final Random random;

    public MovementSystem() {
        this(new Random(1337L)); // Deterministic seed for reproducible simulation
    }

    public MovementSystem(Random random) {
        this.random = random;
    }

    @Override
    public String name() {
        return "MovementSystem";
    }

    @Override
    public void update(SimulationClock.StepResult step, SimulationClock clock, EntityWorld entities, RuntimeState state) {
        if (step.serverTicksAdvanced() <= 0) return;

        List<RuntimeEntity> snapshot = new ArrayList<>(entities.all());
        for (RuntimeEntity entity : snapshot) {
            if (entity instanceof NpcEntity npc && npc.wanderRadius() > 0) {
                // If idle, 25% chance per server tick to wander 1 tile within wander bounds
                if (random.nextInt(4) == 0) {
                    int dx = random.nextInt(3) - 1; // -1, 0, 1
                    int dy = random.nextInt(3) - 1;
                    double targetX = npc.worldX() + dx;
                    double targetY = npc.worldY() + dy;

                    if (Math.abs(targetX - npc.spawnX()) <= npc.wanderRadius()
                            && Math.abs(targetY - npc.spawnY()) <= npc.wanderRadius()) {
                        int orient = npc.orientation();
                        if (dx != 0 || dy != 0) {
                            orient = (int) Math.round(Math.atan2(dx, dy) * (1024.0 / Math.PI));
                            if (orient < 0) orient += 2048;
                        }
                        RuntimeEntity moved = npc.withPosition(targetX, targetY)
                                .withOrientation(orient)
                                .withServerState("walking");
                        entities.add(moved);
                    }
                }
            }
        }
    }
}
