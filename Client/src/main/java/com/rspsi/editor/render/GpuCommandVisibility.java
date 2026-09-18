package com.rspsi.editor.render;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Per-frame, camera-dependent draw-skip decision over an already-uploaded
 * {@link GpuUploadPlan}.
 *
 * <p>This replaces the retired {@code OcclusionPlanFilter}, which rebuilt a
 * new per-triangle index list and command list every frame and folded the
 * camera into the plan's re-upload fingerprint. That caused a full GPU
 * buffer re-upload on every camera-movement frame and shattered merged draw
 * commands into single-triangle commands near any occluder. This type never
 * touches vertex/index data or the plan's geometry fingerprint: it only
 * decides which already-merged {@link GpuDrawCommand}s to submit this frame,
 * at the same granularity {@link GpuUploadPlanBuilder} produced them.</p>
 */
public final class GpuCommandVisibility {
    /**
     * Occlusion is an optimization, never a prerequisite for drawing. A real
     * region can contain hundreds of thousands of triangles and thousands of
     * wall planes; evaluating every command against every plane would stall
     * the UI thread during the first frame. Keep the exact resolver for small
     * fixtures and deliberately draw all commands when the broad-phase budget
     * is exceeded.
     */
    private static final int MAX_OCCLUSION_COMMANDS = 10_000;
    private static final long MAX_OCCLUSION_TESTS = 250_000L;

    private final BitSet occluded;
    private final int commandCount;
    private final boolean occlusionApplied;

    private GpuCommandVisibility(BitSet occluded, int commandCount, boolean occlusionApplied) {
        this.occluded = occluded;
        this.commandCount = commandCount;
        this.occlusionApplied = occlusionApplied;
    }

    public static GpuCommandVisibility of(GpuUploadPlan plan, CameraState camera) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        List<GpuDrawCommand> commands = plan.commands();
        BitSet occluded = new BitSet(commands.size());
        long estimatedTests = (long) commands.size() * plan.occluders().size();
        boolean occlusionApplied = !plan.occluders().isEmpty()
                && commands.size() <= MAX_OCCLUSION_COMMANDS
                && estimatedTests <= MAX_OCCLUSION_TESTS;
        if (occlusionApplied) {
            for (int index = 0; index < commands.size(); index++) {
                if (SceneOcclusionResolver.occludesCommand(index, commands.get(index), plan, camera,
                        plan.occluders())) {
                    occluded.set(index);
                }
            }
        }
        return new GpuCommandVisibility(occluded, commands.size(), occlusionApplied);
    }

    /** True when the camera-dependent occluder resolver ran for this frame. */
    public boolean occlusionApplied() {
        return occlusionApplied;
    }

    /** Returns true when the command at {@code commandIndex} should be drawn this frame. */
    public boolean visible(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= commandCount) {
            throw new IndexOutOfBoundsException("commandIndex " + commandIndex);
        }
        return !occluded.get(commandIndex);
    }
}
