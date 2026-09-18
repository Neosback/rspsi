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
    private final BitSet occluded;
    private final int commandCount;

    private GpuCommandVisibility(BitSet occluded, int commandCount) {
        this.occluded = occluded;
        this.commandCount = commandCount;
    }

    public static GpuCommandVisibility of(GpuUploadPlan plan, CameraState camera) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        List<GpuDrawCommand> commands = plan.commands();
        BitSet occluded = new BitSet(commands.size());
        if (!plan.occluders().isEmpty()) {
            for (int index = 0; index < commands.size(); index++) {
                if (SceneOcclusionResolver.occludesCommand(index, commands.get(index), plan, camera,
                        plan.occluders())) {
                    occluded.set(index);
                }
            }
        }
        return new GpuCommandVisibility(occluded, commands.size());
    }

    /** Returns true when the command at {@code commandIndex} should be drawn this frame. */
    public boolean visible(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= commandCount) {
            throw new IndexOutOfBoundsException("commandIndex " + commandIndex);
        }
        return !occluded.get(commandIndex);
    }
}
