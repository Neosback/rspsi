package com.rspsi.studio.map

import com.rspsi.cache.map.OsrsProjectSessionLoader
import com.rspsi.editor.EditorSession
import com.rspsi.editor.model.WorldRegion
import com.rspsi.editor.render.AnimationRefreshScheduler
import com.rspsi.editor.render.CameraState
import com.rspsi.editor.render.GpuScenePacket
import com.rspsi.editor.render.GpuUploadPlan
import com.rspsi.editor.render.GpuZonedUploadPlan
import com.rspsi.editor.render.IncrementalGpuUploadPlanBuilder
import com.rspsi.editor.render.RenderScene
import com.rspsi.editor.render.RenderWindowScene
import com.rspsi.editor.render.SceneFocus

/**
 * One loaded Map Studio region and everything derived from it, as one immutable value.
 *
 * Every rebuild (edit, var change, animation frame, plan setting) produces a new value that
 * shares the unchanged parts of the previous one, so the viewport always draws one
 * consistent generation: window scene, packet, plan and planner state all belong together.
 */
@JvmRecord
data class LoadedMapScene(
    /** The opened region: project, cache compatibility and the editable region itself. */
    val opened: OsrsProjectSessionLoader.OpenedProject,
    /** The edit session of the active region; the authored source of truth. */
    val session: EditorSession,
    /** Resolved scene of the region plus its read-only neighbour ring. */
    val windowScene: RenderWindowScene,
    /** Semantic scene of the active region for core modules (no model packets). */
    val renderScene: RenderScene,
    /** GPU-neutral per-tile packet built from [windowScene]. */
    val packet: GpuScenePacket,
    /** Flat upload plan holding every plane; plane choice is a draw-time filter. */
    val plan: GpuUploadPlan,
    /** [plan] split into 8x8 zones for upload reuse. */
    val zonedPlan: GpuZonedUploadPlan,
    /** Incremental planner whose fragments produced [plan]; forked for the next rebuild. */
    val planBuilder: IncrementalGpuUploadPlanBuilder,
    /** Render-config revision the plan was built with (see [RenderConfigState]). */
    val settingsRevision: Long,
    /** Camera to start from when this region was first loaded. */
    val camera: CameraState,
    /** Client cycle whose animation frames the scene shows. */
    val animationCycle: Int,
    /** Next cycle at which an animated location changes frame, or [AnimationRefreshScheduler.NONE]. */
    val nextAnimationRefreshCycle: Int,
    /** Loaded neighbours, keyed by region id; read-only context for stitching and the ring. */
    val contextRegions: Map<Int, WorldRegion>,
    /** Tiles emitted: the active region plus the context ring. */
    val focus: SceneFocus,
) {
    init {
        require(animationCycle >= 0) { "Animation cycle cannot be negative" }
        require(nextAnimationRefreshCycle >= AnimationRefreshScheduler.NONE) { "Invalid next animation refresh cycle" }
        require(nextAnimationRefreshCycle < 0 || nextAnimationRefreshCycle > animationCycle) {
            "Next animation refresh cycle must be in the future"
        }
    }

    /** The editable region. */
    fun region(): WorldRegion = opened.worldRegion()
}
