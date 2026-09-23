package com.rspsi.editor.render;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private final BitSet hidden;
    private final int commandCount;
    private final boolean occlusionApplied;
    private final boolean extendedSceneApplied;
    private final boolean cameraInExtendedScene;
    private final boolean cameraInExtendedBorder;

    private GpuCommandVisibility(BitSet hidden, int commandCount, boolean occlusionApplied,
                                 boolean extendedSceneApplied,
                                 boolean cameraInExtendedScene,
                                 boolean cameraInExtendedBorder) {
        this.hidden = hidden;
        this.commandCount = commandCount;
        this.occlusionApplied = occlusionApplied;
        this.extendedSceneApplied = extendedSceneApplied;
        this.cameraInExtendedScene = cameraInExtendedScene;
        this.cameraInExtendedBorder = cameraInExtendedBorder;
    }

    public static GpuCommandVisibility of(GpuUploadPlan plan, CameraState camera) {
        Objects.requireNonNull(plan, "plan");
        return of(plan, camera, plan.occluders(), plan.sceneWindow());
    }

    public static GpuCommandVisibility of(GpuCommandGeometry geometry, CameraState camera,
                                          List<SceneOccluder> occluders) {
        return of(geometry, camera, occluders, Optional.empty());
    }

    public static GpuCommandVisibility of(GpuCommandGeometry geometry, CameraState camera,
                                          List<SceneOccluder> occluders,
                                          Optional<SceneWindow> sceneWindow) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        Objects.requireNonNull(sceneWindow, "sceneWindow");
        int commandCount = geometry.commandCount();
        BitSet hidden = new BitSet(commandCount);

        ExtendedSceneZoneTraversal.Frame extendedFrame = sceneWindow
                .map(ExtendedSceneZoneTraversal::new)
                .map(traversal -> traversal.frame(camera))
                .orElse(null);
        boolean extendedSceneApplied = extendedFrame != null
                && extendedFrame.traversal().applies();
        if (extendedSceneApplied) {
            for (int index = 0; index < commandCount; index++) {
                if (!extendedFrame.includes(geometry.command(index))) {
                    hidden.set(index);
                }
            }
        }

        long estimatedTests = (long) commandCount * occluders.size();
        boolean occlusionApplied = !occluders.isEmpty()
                && commandCount <= MAX_OCCLUSION_COMMANDS
                && estimatedTests <= MAX_OCCLUSION_TESTS;
        if (occlusionApplied) {
            for (int index = 0; index < commandCount; index++) {
                if (hidden.get(index)) {
                    continue;
                }
                GpuDrawCommand command = geometry.command(index);
                if (SceneOcclusionResolver.occludesCommand(
                        index, command, geometry, camera, occluders)) {
                    hidden.set(index);
                }
            }
        }
        return new GpuCommandVisibility(hidden, commandCount, occlusionApplied,
                extendedSceneApplied,
                extendedFrame == null || extendedFrame.cameraInExtendedScene(),
                extendedFrame != null && extendedFrame.cameraInBorder());
    }

    /** True when the camera-dependent occluder resolver ran for this frame. */
    public boolean occlusionApplied() {
        return occlusionApplied;
    }

    /** True when the top-level 184x184 extended scene gate participated this frame. */
    public boolean extendedSceneApplied() {
        return extendedSceneApplied;
    }

    /** True when the camera lies within the current extended scene traversal window. */
    public boolean cameraInExtendedScene() {
        return cameraInExtendedScene;
    }

    /** True when the camera currently occupies the five-zone extended border. */
    public boolean cameraInExtendedBorder() {
        return cameraInExtendedBorder;
    }

    /** Returns true when the command at {@code commandIndex} should be drawn this frame. */
    public boolean visible(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= commandCount) {
            throw new IndexOutOfBoundsException("commandIndex " + commandIndex);
        }
        return !hidden.get(commandIndex);
    }
}
