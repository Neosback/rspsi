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
        return compute(geometry, camera, occluders, sceneWindow,
                null, null, false);
    }

    private static GpuCommandVisibility compute(
            GpuCommandGeometry geometry,
            CameraState camera,
            List<SceneOccluder> occluders,
            Optional<SceneWindow> sceneWindow,
            BitSet precomputedExtendedHidden,
            ExtendedSceneZoneTraversal precomputedTraversal,
            boolean precomputedExtendedApplied) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        Objects.requireNonNull(sceneWindow, "sceneWindow");

        int commandCount = geometry.commandCount();
        BitSet hidden;
        ExtendedSceneZoneTraversal traversal = precomputedTraversal;
        boolean extendedSceneApplied = precomputedExtendedApplied;

        if (precomputedExtendedHidden != null) {
            hidden = (BitSet) precomputedExtendedHidden.clone();
        } else {
            hidden = new BitSet(commandCount);
            traversal = sceneWindow.map(ExtendedSceneZoneTraversal::new).orElse(null);
            extendedSceneApplied = traversal != null && traversal.applies();
            if (extendedSceneApplied) {
                for (int index = 0; index < commandCount; index++) {
                    if (!traversal.includes(geometry.command(index).tile())) {
                        hidden.set(index);
                    }
                }
            }
        }

        ExtendedSceneZoneTraversal.Frame extendedFrame =
                traversal == null ? null : traversal.frame(camera);

        long estimatedTests = (long) commandCount * occluders.size();
        boolean occlusionApplied = !occluders.isEmpty()
                && commandCount <= MAX_OCCLUSION_COMMANDS
                && estimatedTests <= MAX_OCCLUSION_TESTS;
        if (occlusionApplied) {
            for (int index = 0; index < commandCount; index++) {
                if (hidden.get(index)) continue;
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

    /**
     * One-entry visibility snapshot cache for a live renderer.
     *
     * <p>The extended-scene membership mask depends only on geometry and the
     * scene window, not on the camera, so it is retained separately across
     * camera movement. The final visibility snapshot is reused completely
     * while geometry, camera, occluders and scene-window identity are
     * unchanged. This removes the steady-frame BitSet allocation and command
     * scan without making visibility global or long-lived.</p>
     */
    public static final class Cache {
        private GpuCommandGeometry geometry;
        private Optional<SceneWindow> sceneWindow = Optional.empty();
        private ExtendedSceneZoneTraversal traversal;
        private BitSet extendedHidden = new BitSet();
        private boolean extendedSceneApplied;

        private CameraState camera;
        private List<SceneOccluder> occluders;
        private GpuCommandVisibility visibility;

        public GpuCommandVisibility resolve(
                GpuCommandGeometry geometry,
                CameraState camera,
                List<SceneOccluder> occluders,
                Optional<SceneWindow> sceneWindow) {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(occluders, "occluders");
            Objects.requireNonNull(sceneWindow, "sceneWindow");

            boolean geometryChanged = this.geometry != geometry;
            boolean windowChanged = this.sceneWindow != sceneWindow;
            if (geometryChanged || windowChanged) {
                rebuildExtendedMask(geometry, sceneWindow);
                this.geometry = geometry;
                this.sceneWindow = sceneWindow;
                this.visibility = null;
                this.camera = null;
                this.occluders = null;
            }

            boolean cameraIndependent = occluders.isEmpty() && traversal == null;
            if (visibility != null
                    && this.occluders == occluders
                    && (cameraIndependent || camera.equals(this.camera))) {
                return visibility;
            }

            visibility = compute(
                    geometry, camera, occluders, sceneWindow,
                    extendedHidden, traversal, extendedSceneApplied);
            this.camera = camera;
            this.occluders = occluders;
            return visibility;
        }

        public GpuCommandVisibility resolve(GpuUploadPlan plan, CameraState camera) {
            Objects.requireNonNull(plan, "plan");
            return resolve(plan, camera, plan.occluders(), plan.sceneWindow());
        }

        public void clear() {
            geometry = null;
            sceneWindow = Optional.empty();
            traversal = null;
            extendedHidden.clear();
            extendedSceneApplied = false;
            camera = null;
            occluders = null;
            visibility = null;
        }

        private void rebuildExtendedMask(
                GpuCommandGeometry geometry,
                Optional<SceneWindow> sceneWindow) {
            traversal = sceneWindow.map(ExtendedSceneZoneTraversal::new).orElse(null);
            extendedSceneApplied = traversal != null && traversal.applies();
            extendedHidden.clear();
            if (!extendedSceneApplied) return;

            int commandCount = geometry.commandCount();
            for (int index = 0; index < commandCount; index++) {
                if (!traversal.includes(geometry.command(index).tile())) {
                    extendedHidden.set(index);
                }
            }
        }
    }

    public boolean occlusionApplied() {
        return occlusionApplied;
    }

    public boolean extendedSceneApplied() {
        return extendedSceneApplied;
    }

    public boolean cameraInExtendedScene() {
        return cameraInExtendedScene;
    }

    public boolean cameraInExtendedBorder() {
        return cameraInExtendedBorder;
    }

    public boolean visible(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= commandCount) {
            throw new IndexOutOfBoundsException("commandIndex " + commandIndex);
        }
        return !hidden.get(commandIndex);
    }
}
