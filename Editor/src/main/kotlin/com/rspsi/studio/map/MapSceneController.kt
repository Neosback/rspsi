package com.rspsi.studio.map

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldLocation
import com.rspsi.editor.render.GpuUploadPlan
import com.rspsi.editor.render.GpuUploadPlanBuilder
import com.rspsi.editor.render.GpuZonedUploadPlan
import com.rspsi.editor.render.GpuZonedUploadPlanBuilder
import com.rspsi.editor.render.RenderConfig
import com.rspsi.editor.render.ScenePresentation
import com.rspsi.editor.simulation.state.RuntimeState
import com.rspsi.studio.rootMessage
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Owns the Map Studio scene lifecycle: loading a region, rebuilding after edits and var
 * changes, refreshing animation frames, and re-planning when plan settings change.
 *
 * All heavy work runs on one loader thread and never blocks the frame; the UI thread calls
 * [poll] once per frame to adopt finished work and schedule the next job. At most one job
 * of each kind is in flight, and edits and var changes win over animation refreshes so a
 * busy animated scene cannot starve them.
 *
 * The controller never touches ImGui, the viewport or the editor host. When a load
 * finishes, [poll] returns a [Loaded] event and the Studio shell binds those.
 */
class MapSceneController(
    /** Presentation (simulated var state) for scene builds of a cache session. */
    private val presentationFor: Function<LoadedOsrsCacheSession, ScenePresentation>,
    /** Current client cycle of the simulation clock. */
    private val clientCycle: IntSupplier,
    /** Current simulated var state; a change rebuilds multilocs. */
    private val varState: Supplier<RuntimeState>,
    initialRender: RenderConfig,
    objectAnimations: Boolean,
) : AutoCloseable {
    /** A finished region load; [focus] is the location to frame, if the request named one. */
    @JvmRecord
    data class Loaded(val scene: LoadedMapScene, val focus: WorldLocation?)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "openrune-scene-loader").apply { isDaemon = true }
    }
    private val diagnostics = AnimationRefreshDiagnostics()

    @Volatile
    private var render = RenderConfigState(initialRender, 0L)

    @Volatile
    private var animationsEnabled = objectAnimations

    private var scene: LoadedMapScene? = null
    private var plan: GpuUploadPlan? = null
    private var zonedPlan: GpuZonedUploadPlan? = null
    private var pickPlan: GpuUploadPlan? = null
    private var pickZonedPlan: GpuZonedUploadPlan? = null
    private var renderedSettingsRevision = -1L
    private var renderedVarState: RuntimeState = RuntimeState.EMPTY

    private var pendingLoad: CompletableFuture<LoadedMapScene>? = null
    private var pendingFocus: WorldLocation? = null
    private var pendingRebuild: CompletableFuture<LoadedMapScene>? = null
    private var pendingRebuildChanges: Set<TileCoordinate> = emptySet()
    private var pendingAnimation: CompletableFuture<LoadedMapScene>? = null

    /** Tiles edited since the last rebuild was scheduled; written by session listeners. */
    private val editedTiles = LinkedHashSet<TileCoordinate>()
    private val edited = AtomicBoolean(false)

    /** Short status line for the Studio shell. */
    @Volatile
    var status: String = "Choose a region to build the scene."

    /** The current scene generation, or null while nothing is loaded. */
    fun scene(): LoadedMapScene? = scene

    /** The plan the viewport draws; follows animation frames. */
    fun plan(): GpuUploadPlan? = plan

    fun zonedPlan(): GpuZonedUploadPlan? = zonedPlan

    /** The plan picks and outlines use: follows loads, edits and settings, not animation frames. */
    fun pickPlan(): GpuUploadPlan? = pickPlan

    fun pickZonedPlan(): GpuZonedUploadPlan? = pickZonedPlan

    fun isLoading(): Boolean = pendingLoad != null

    /** The compiled render configuration and its plan revision. */
    fun renderState(): RenderConfigState = render

    /** Applies newly compiled render settings; only plan-shaping changes re-plan. */
    fun updateRenderConfig(next: RenderConfig) {
        render = render.withConfig(next)
    }

    /**
     * Starts loading [location]'s region, replacing whatever is loaded. The region's tile is
     * framed once loaded when [location] names a tile or a plane.
     */
    fun load(cache: LoadedOsrsCacheSession, location: WorldLocation) {
        unload()
        status = "Loading ${location.describe()}..."
        pendingFocus = if (location.isTile() || location.plane >= 0) location else null
        val cycle = clientCycle.asInt
        val presentation = presentation(cache)
        val state = render
        val animations = animationsEnabled
        pendingLoad = CompletableFuture.supplyAsync({
            MapScenePipeline.buildInitial(cache, location.regionX(), location.regionY(), cycle,
                presentation, state, animations)
        }, executor)
    }

    /** Drops the loaded scene and cancels every job in flight. */
    fun unload() {
        cancelPending()
        scene = null
        plan = null
        zonedPlan = null
        pickPlan = null
        pickZonedPlan = null
        pendingFocus = null
        renderedSettingsRevision = -1L
    }

    /**
     * Turns object animations on or off. The scene is refreshed once at the current cycle
     * (or back to cycle 0 when turning them off) so it shows the right frame immediately.
     */
    fun setObjectAnimations(enabled: Boolean, cache: LoadedOsrsCacheSession?) {
        if (animationsEnabled == enabled) return
        animationsEnabled = enabled
        pendingAnimation?.cancel(true)
        pendingAnimation = null
        val base = scene ?: return
        if (cache == null) return
        scheduleAnimationRefresh(cache, base, if (enabled) clientCycle.asInt else 0)
    }

    /**
     * Adopts finished work and schedules the next job; call once per frame on the UI thread.
     * Returns the scene when a region load has just finished, otherwise null.
     */
    fun poll(cache: LoadedOsrsCacheSession?): Loaded? {
        val loaded = adoptLoad()
        adoptAnimationRefresh()
        adoptRebuild()
        if (cache != null) scheduleWork(cache)
        replanForSettings()
        return loaded
    }

    private fun adoptLoad(): Loaded? {
        val job = pendingLoad ?: return null
        if (!job.isDone) return null
        pendingLoad = null
        return try {
            val loaded = job.join()
            adopt(loaded, pick = true)
            renderedVarState = varState.get()
            loaded.session.addChangeListener { tiles: Set<TileCoordinate>? ->
                synchronized(editedTiles) { if (tiles != null) editedTiles.addAll(tiles) }
                edited.set(true)
            }
            val region = loaded.region()
            status = "Region ${region.regionX},${region.regionY} ready."
            Loaded(loaded, pendingFocus).also { pendingFocus = null }
        } catch (failure: RuntimeException) {
            // The status bar has room for a short message only; keep the cause in the log.
            LOGGER.error("Region load failed", failure)
            status = "Unable to load region: ${rootMessage(failure)}"
            null
        }
    }

    private fun adoptAnimationRefresh() {
        val job = pendingAnimation ?: return
        if (!job.isDone) return
        pendingAnimation = null
        try {
            adopt(job.join(), pick = false)
        } catch (failure: RuntimeException) {
            LOGGER.error("Animation refresh failed", failure)
        }
    }

    private fun adoptRebuild() {
        val job = pendingRebuild ?: return
        if (!job.isDone) return
        pendingRebuild = null
        try {
            adopt(job.join(), pick = true)
        } catch (failure: RuntimeException) {
            LOGGER.error("Scene rebuild failed", failure)
            // Keep the edits: they rebuild again next frame.
            synchronized(editedTiles) { editedTiles.addAll(pendingRebuildChanges) }
            edited.set(true)
        } finally {
            pendingRebuildChanges = emptySet()
        }
    }

    private fun scheduleWork(cache: LoadedOsrsCacheSession) {
        val base = scene ?: return
        if (pendingRebuild != null || pendingAnimation != null) return
        val vars = varState.get()
        if (vars != renderedVarState) {
            // The simulated player's vars changed: multilocs may show another state, which
            // authored tile deltas cannot see, so rebuild fully.
            renderedVarState = vars
            scheduleRebuild(cache, base, emptySet(), varStateChanged = true)
            return
        }
        if (edited.compareAndSet(true, false)) {
            val tiles = synchronized(editedTiles) { editedTiles.toSet().also { editedTiles.clear() } }
            if (tiles.isNotEmpty()) {
                pendingRebuildChanges = tiles
                scheduleRebuild(cache, base, tiles, varStateChanged = false)
            }
            return
        }
        // Paused animations keep the frame the scene last showed.
        if (!animationsEnabled) return
        val next = base.nextAnimationRefreshCycle
        val cycle = clientCycle.asInt
        if (next >= 0 && cycle >= next) scheduleAnimationRefresh(cache, base, cycle)
    }

    private fun scheduleRebuild(cache: LoadedOsrsCacheSession, base: LoadedMapScene,
                                tiles: Set<TileCoordinate>, varStateChanged: Boolean) {
        val presentation = presentation(cache)
        val state = render
        val animations = animationsEnabled
        pendingRebuild = CompletableFuture.supplyAsync({
            MapScenePipeline.rebuild(cache, base, tiles, varStateChanged, presentation, state, animations)
        }, executor)
    }

    private fun scheduleAnimationRefresh(cache: LoadedOsrsCacheSession, base: LoadedMapScene, cycle: Int) {
        val presentation = presentation(cache)
        val state = render
        val animations = animationsEnabled
        pendingAnimation = CompletableFuture.supplyAsync({
            MapScenePipeline.refreshAnimations(cache, base, cycle, presentation, state, animations, diagnostics)
        }, executor)
    }

    /**
     * Re-plans on the UI thread when a plan-shaping setting changed and no background job
     * has picked the new revision up yet. The packet is unchanged, so this is plan work only.
     */
    private fun replanForSettings() {
        val current = scene ?: return
        val state = render
        if (renderedSettingsRevision == state.revision) return
        val replanned = GpuUploadPlanBuilder().build(state.config.forPlan().apply(current.packet))
        val zoned = GpuZonedUploadPlanBuilder().build(replanned)
        plan = replanned
        zonedPlan = zoned
        pickPlan = replanned
        pickZonedPlan = zoned
        renderedSettingsRevision = state.revision
    }

    /** The session's presentation with this controller's object-animation toggle applied. */
    private fun presentation(cache: LoadedOsrsCacheSession): ScenePresentation =
        presentationFor.apply(cache).withObjectAnimations(animationsEnabled)

    private fun adopt(next: LoadedMapScene, pick: Boolean) {
        scene = next
        plan = next.plan
        zonedPlan = next.zonedPlan
        if (pick) {
            pickPlan = next.plan
            pickZonedPlan = next.zonedPlan
        }
        renderedSettingsRevision = next.settingsRevision
    }

    private fun cancelPending() {
        pendingLoad?.cancel(true)
        pendingLoad = null
        pendingRebuild?.cancel(true)
        pendingRebuild = null
        pendingAnimation?.cancel(true)
        pendingAnimation = null
        pendingRebuildChanges = emptySet()
        edited.set(false)
        synchronized(editedTiles) { editedTiles.clear() }
    }

    override fun close() {
        cancelPending()
        executor.shutdownNow()
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(MapSceneController::class.java)
    }
}
