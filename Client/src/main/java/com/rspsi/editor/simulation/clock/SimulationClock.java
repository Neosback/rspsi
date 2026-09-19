package com.rspsi.editor.simulation.clock;

/**
 * Multi-domain simulation clock coordinating independent time bases:
 * <ul>
 *     <li><b>Render Clock</b>: Runs at display/vsync rate (e.g. 60–144+ FPS).</li>
 *     <li><b>Client Cycle</b>: Runs at 50 Hz (20 ms per cycle) for animations, UI, and client effects.</li>
 *     <li><b>Server Tick</b>: Runs at ~600 ms (~1.667 Hz / exactly 30 client cycles) for movement, server events, and AI.</li>
 * </ul>
 *
 * <p>Supports interactive debugger controls: play, pause, single-stepping, and speed scaling.</p>
 */
public final class SimulationClock {
    public static final long NANOS_PER_CLIENT_CYCLE = 20_000_000L; // 20 ms (50 Hz)
    public static final long NANOS_PER_SERVER_TICK = 600_000_000L; // 600 ms (~1.67 Hz)
    public static final int CYCLES_PER_SERVER_TICK = 30;

    public record StepResult(int clientCyclesAdvanced, int serverTicksAdvanced) {
        public boolean hasAdvanced() {
            return clientCyclesAdvanced > 0 || serverTicksAdvanced > 0;
        }
    }

    private boolean paused = false;
    private float speed = 1.0f;
    private long totalClientCycles = 0L;
    private long totalServerTicks = 0L;

    private long cycleAccumulatorNanos = 0L;
    private long tickAccumulatorNanos = 0L;

    public boolean isPaused() {
        return paused;
    }

    public void pause() {
        this.paused = true;
    }

    public void play() {
        this.paused = false;
    }

    public void togglePause() {
        this.paused = !this.paused;
    }

    public float speed() {
        return speed;
    }

    public void setSpeed(float speed) {
        if (speed <= 0.0f || !Float.isFinite(speed)) {
            throw new IllegalArgumentException("Simulation speed must be positive: " + speed);
        }
        this.speed = speed;
    }

    public long clientCycles() {
        return totalClientCycles;
    }

    public long serverTicks() {
        return totalServerTicks;
    }

    /** Fraction [0.0, 1.0) of progress toward the next client cycle for smooth interpolation. */
    public float cycleInterpolation() {
        return (float) cycleAccumulatorNanos / (float) NANOS_PER_CLIENT_CYCLE;
    }

    /** Fraction [0.0, 1.0) of progress toward the next server tick. */
    public float tickInterpolation() {
        return (float) tickAccumulatorNanos / (float) NANOS_PER_SERVER_TICK;
    }

    /**
     * Advances simulation time by the specified real elapsed nanoseconds.
     * When paused, does not advance unless single-stepping.
     */
    public StepResult advance(long deltaNanos) {
        if (deltaNanos <= 0 || paused) {
            return new StepResult(0, 0);
        }

        long scaledNanos = (long) (deltaNanos * speed);
        cycleAccumulatorNanos += scaledNanos;
        tickAccumulatorNanos += scaledNanos;

        int cycles = 0;
        while (cycleAccumulatorNanos >= NANOS_PER_CLIENT_CYCLE) {
            cycleAccumulatorNanos -= NANOS_PER_CLIENT_CYCLE;
            totalClientCycles++;
            cycles++;
        }

        int ticks = 0;
        while (tickAccumulatorNanos >= NANOS_PER_SERVER_TICK) {
            tickAccumulatorNanos -= NANOS_PER_SERVER_TICK;
            totalServerTicks++;
            ticks++;
        }

        return new StepResult(cycles, ticks);
    }

    /** Manually steps a single 50Hz client cycle when paused. */
    public StepResult stepClientCycle() {
        totalClientCycles++;
        int ticks = 0;
        if (totalClientCycles % CYCLES_PER_SERVER_TICK == 0) {
            totalServerTicks++;
            ticks = 1;
        }
        cycleAccumulatorNanos = 0L;
        return new StepResult(1, ticks);
    }

    /** Manually steps a single ~600ms server tick (30 client cycles) when paused. */
    public StepResult stepServerTick() {
        totalClientCycles += CYCLES_PER_SERVER_TICK;
        totalServerTicks++;
        cycleAccumulatorNanos = 0L;
        tickAccumulatorNanos = 0L;
        return new StepResult(CYCLES_PER_SERVER_TICK, 1);
    }

    public void reset() {
        totalClientCycles = 0L;
        totalServerTicks = 0L;
        cycleAccumulatorNanos = 0L;
        tickAccumulatorNanos = 0L;
    }
}
