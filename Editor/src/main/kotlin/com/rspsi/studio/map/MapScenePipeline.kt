package com.rspsi.studio.map

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.EditorSession
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldRegion
import com.rspsi.editor.model.WorldRegionWindow
import com.rspsi.editor.model.WorldTileAddress
import com.rspsi.editor.render.AnimationRefreshScheduler
import com.rspsi.editor.render.CameraState
import com.rspsi.editor.render.GpuScenePacketBuilder
import com.rspsi.editor.render.IncrementalGpuUploadPlanBuilder
import com.rspsi.editor.render.RenderChanges
import com.rspsi.editor.render.RenderSceneBuilder
import com.rspsi.editor.render.RenderWindowScene
import com.rspsi.editor.render.RenderWindowSceneBuilder
import com.rspsi.editor.render.SceneFocus
import com.rspsi.editor.render.ScenePresentation
import com.rspsi.editor.render.SceneWindow
import com.rspsi.editor.render.compiler.IncrementalRenderWindowSceneCompiler
import java.util.concurrent.CompletableFuture

/**
 * Builds [LoadedMapScene] generations: the first load of a region, rebuilds after edits or
 * var changes, and animation refreshes.
 *
 * Stateless and UI-free: every input is a parameter, so the same calls run on the loader
 * thread, in tests and in benchmarks. The flow is the one in AGENTS.md: authored window,
 * then resolved scene, then GPU packet, then the incremental upload plan.
 */
internal object MapScenePipeline {
    /** Starting camera: behind the region centre, looking north and down. */
    private const val START_CAMERA_HEIGHT = -2400.0f
    private const val START_CAMERA_BACK_OFFSET = 4200.0f
    private const val START_CAMERA_PITCH_DEGREES = 28.0

    /**
     * Loads region ([regionX], [regionY]) with its neighbours as read-only context and
     * builds its first scene generation.
     */
    fun buildInitial(
        cache: LoadedOsrsCacheSession,
        regionX: Int,
        regionY: Int,
        clientCycle: Int,
        presentation: ScenePresentation,
        render: RenderConfigState,
        objectAnimations: Boolean,
    ): LoadedMapScene {
        val totalStart = System.nanoTime()
        val definitions = cache.bundle().definitions()
        val opened = cache.openRegion(regionX, regionY)
        val region = opened.worldRegion()
        // Neighbours stitch the shared edges, feed underlay blending and contouring, and show
        // a ring of tiles around the active region. They are never edited here.
        val contextRegions = HashMap(cache.bundle().openWindowAround(regionX, regionY, 1).regions())
        contextRegions.remove(region.regionId())
        val window = contextWindow(region, contextRegions)
        val focus = SceneFocus.aroundRegion(regionX, regionY, SceneFocus.CONTEXT_RING_TILES)

        // The semantic scene only reads the active region; build it alongside the window
        // scene instead of after the whole GPU path.
        val semanticStart = System.nanoTime()
        val semantic = CompletableFuture.supplyAsync {
            val scene = RenderSceneBuilder(definitions, presentation).withoutModelPackets()
                .build(region.document, clientCycle)
            scene to System.nanoTime() - semanticStart
        }

        val windowStart = System.nanoTime()
        val scene = RenderWindowSceneBuilder(definitions, presentation).build(window, clientCycle, focus)
        val windowNanos = System.nanoTime() - windowStart

        val packetStart = System.nanoTime()
        val packet = GpuScenePacketBuilder().build(SceneWindow.from(window), scene)
        val packetNanos = System.nanoTime() - packetStart

        val planStart = System.nanoTime()
        val planBuilder = IncrementalGpuUploadPlanBuilder()
        val initialPlan = planBuilder.buildInitial(render.config.forPlan().apply(packet))
        val planNanos = System.nanoTime() - planStart

        val (renderScene, renderSceneNanos) = semantic.join()
        var session: EditorSession = opened.region().session()
        if (!session.canEdit()) session = EditorSession(region.document, region.window())

        SceneBuildMetrics(windowNanos, packetNanos, planNanos, renderSceneNanos,
            System.nanoTime() - totalStart, initialPlan.plan()).log("initial", regionX, regionY)
        return LoadedMapScene(
            opened, session, scene, renderScene, packet, initialPlan.plan(), initialPlan.zonedPlan(),
            planBuilder, render.revision, startCamera(regionX, regionY), clientCycle,
            nextAnimationCycle(scene, cache, clientCycle, objectAnimations), contextRegions.toMap(), focus,
        )
    }

    /**
     * The next generation after [changedTiles] were edited in the active region, or a full
     * rebuild when [varStateChanged] (a multiloc may now show another state, which authored
     * tile deltas cannot see).
     */
    fun rebuild(
        cache: LoadedOsrsCacheSession,
        base: LoadedMapScene,
        changedTiles: Set<TileCoordinate>,
        varStateChanged: Boolean,
        presentation: ScenePresentation,
        render: RenderConfigState,
        objectAnimations: Boolean,
    ): LoadedMapScene {
        val totalStart = System.nanoTime()
        val definitions = cache.bundle().definitions()
        val region = base.region()
        val window = contextWindow(region, base.contextRegions)
        val changedWorldTiles = changedTiles.mapTo(HashSet()) { tile ->
            WorldTileAddress.of(region.regionX * WorldRegion.REGION_SIZE + tile.x,
                region.regionY * WorldRegion.REGION_SIZE + tile.y, tile.plane)
        }

        val windowStart = System.nanoTime()
        val compiler = IncrementalRenderWindowSceneCompiler(definitions, presentation, base.focus)
        val windowUpdate = if (varStateChanged) {
            compiler.compileFull(window, base.animationCycle, "simulated var state changed")
        } else {
            compiler.compile(base.windowScene, window, changedWorldTiles, base.animationCycle)
        }
        val scene = windowUpdate.scene()
        val windowNanos = System.nanoTime() - windowStart

        val packetStart = System.nanoTime()
        val sceneWindow = SceneWindow.from(window)
        val packetBuilder = GpuScenePacketBuilder()
        val packetUpdate = if (windowUpdate.fullRebuild()) {
            val full = packetBuilder.build(sceneWindow, scene)
            GpuScenePacketBuilder.IncrementalBuildResult(full, full.tiles().size, 0, true)
        } else {
            packetBuilder.buildIncremental(base.packet, sceneWindow, scene, windowUpdate.dirtyWorldZones())
        }
        val packet = packetUpdate.packet()
        val packetNanos = System.nanoTime() - packetStart

        val planStart = System.nanoTime()
        val visiblePacket = render.config.forPlan().apply(packet)
        val planBuilder = base.planBuilder.fork()
        val planUpdate = if (windowUpdate.fullRebuild() || render.revision != base.settingsRevision) {
            planBuilder.invalidateAll()
            planBuilder.buildInitial(visiblePacket)
        } else {
            planBuilder.build(visiblePacket, windowUpdate.dirtyWorldZones())
        }
        val planNanos = System.nanoTime() - planStart

        val renderSceneStart = System.nanoTime()
        val semanticBuilder = RenderSceneBuilder(definitions, presentation).withoutModelPackets()
        val renderScene = if (varStateChanged) {
            semanticBuilder.build(region.document, base.animationCycle)
        } else {
            semanticBuilder.update(base.renderScene, RenderChanges(changedTiles), base.animationCycle)
        }
        val renderSceneNanos = System.nanoTime() - renderSceneStart

        SceneBuildMetrics(windowNanos, packetNanos, planNanos, renderSceneNanos,
            System.nanoTime() - totalStart, planUpdate.plan()).log("edit", region.regionX, region.regionY)
        logEditRebuild(
            "Map scene edit window mode={}, reason={}, dirtyZones={}, worldZones={}, compiledVisibleTiles={}, " +
                "packetRebuilt={}, packetReused={}, planRebuilt={}, planReused={}",
            if (windowUpdate.fullRebuild()) "full" else "incremental", windowUpdate.reason(),
            windowUpdate.dirtyZones().size, windowUpdate.dirtyWorldZones().size, windowUpdate.compiledVisibleTiles(),
            packetUpdate.rebuiltTiles(), packetUpdate.reusedTiles(), planUpdate.rebuiltTiles(), planUpdate.reusedTiles(),
        )
        return base.copy(
            windowScene = scene, renderScene = renderScene, packet = packet, plan = planUpdate.plan(),
            zonedPlan = planUpdate.zonedPlan(), planBuilder = planBuilder, settingsRevision = render.revision,
            nextAnimationRefreshCycle = nextAnimationCycle(scene, cache, base.animationCycle, objectAnimations),
        )
    }

    /**
     * Advances animated locations to [clientCycle]. Only zones whose frames changed are
     * re-packed and re-planned; a changed plan setting forces a full re-plan.
     */
    fun refreshAnimations(
        cache: LoadedOsrsCacheSession,
        base: LoadedMapScene,
        clientCycle: Int,
        presentation: ScenePresentation,
        render: RenderConfigState,
        objectAnimations: Boolean,
        diagnostics: AnimationRefreshDiagnostics,
    ): LoadedMapScene {
        val totalStart = System.nanoTime()
        val heapBefore = usedHeapBytes()
        val definitions = cache.bundle().definitions()

        val windowStart = System.nanoTime()
        val animation = RenderWindowSceneBuilder(definitions, presentation)
            .refreshAnimations(base.windowScene, clientCycle)
        val windowRefreshNanos = System.nanoTime() - windowStart
        val scene = animation.scene()

        val semanticStart = System.nanoTime()
        val renderScene = RenderSceneBuilder(definitions, presentation).withoutModelPackets()
            .refreshAnimations(base.renderScene, clientCycle)
        val semanticNanos = System.nanoTime() - semanticStart

        val packetStart = System.nanoTime()
        val settingsChanged = render.revision != base.settingsRevision
        val packetUpdate = if (animation.dirtyZones().isEmpty()) null else GpuScenePacketBuilder()
            .buildChangedTiles(base.packet, SceneWindow.from(scene.window()), scene, animation.changedAddresses())
        val packet = packetUpdate?.packet() ?: base.packet
        val packetNanos = System.nanoTime() - packetStart

        val planStart = System.nanoTime()
        var planBuilder = base.planBuilder
        val planUpdate = if (settingsChanged || !animation.dirtyZones().isEmpty()) {
            val visiblePacket = render.config.forPlan().apply(packet)
            planBuilder = base.planBuilder.fork()
            if (settingsChanged) {
                planBuilder.invalidateAll()
                planBuilder.buildInitial(visiblePacket)
            } else {
                planBuilder.build(visiblePacket, animation.dirtyZones())
            }
        } else {
            null
        }
        val planNanos = System.nanoTime() - planStart

        val next = base.copy(
            windowScene = scene, renderScene = renderScene, packet = packet,
            plan = planUpdate?.plan() ?: base.plan, zonedPlan = planUpdate?.zonedPlan() ?: base.zonedPlan,
            planBuilder = planBuilder, settingsRevision = render.revision, animationCycle = clientCycle,
            nextAnimationRefreshCycle = nextAnimationCycle(scene, cache, clientCycle, objectAnimations),
        )
        diagnostics.record(AnimationPipelineMetrics(
            clientCycle, animation.timings(), windowRefreshNanos, semanticNanos, packetNanos, planNanos,
            System.nanoTime() - totalStart, animation.dirtyZones().size, animation.changedTiles(),
            animation.rebuiltModelTiles(), packetUpdate != null, packetUpdate?.rebuiltTiles() ?: 0,
            packetUpdate?.reusedTiles() ?: 0, planUpdate != null, planUpdate?.rebuiltTiles() ?: 0,
            planUpdate?.reusedTiles() ?: 0, planUpdate?.rebuiltZones() ?: 0, planUpdate?.reusedZones() ?: 0,
            heapBefore, usedHeapBytes(),
        ))
        return next
    }

    /** The live edited region inside the smallest window that also holds its loaded neighbours. */
    fun contextWindow(center: WorldRegion, context: Map<Int, WorldRegion>): WorldRegionWindow {
        val regions = HashMap(context)
        regions[center.regionId()] = center
        val minX = regions.values.minOf { it.regionX }
        val minY = regions.values.minOf { it.regionY }
        val maxX = regions.values.maxOf { it.regionX }
        val maxY = regions.values.maxOf { it.regionY }
        return WorldRegionWindow(minX, minY, maxX - minX + 1, maxY - minY + 1, regions)
    }

    private fun nextAnimationCycle(
        scene: RenderWindowScene,
        cache: LoadedOsrsCacheSession,
        cycle: Int,
        objectAnimations: Boolean,
    ): Int = if (objectAnimations) {
        AnimationRefreshScheduler.nextPresentationCycle(scene, cache.bundle().definitions(), cycle)
    } else {
        AnimationRefreshScheduler.NONE
    }

    private fun startCamera(regionX: Int, regionY: Int): CameraState {
        val centerX = (regionX * WorldRegion.REGION_SIZE + WorldRegion.REGION_SIZE / 2) * 128.0f
        val centerZ = (regionY * WorldRegion.REGION_SIZE + WorldRegion.REGION_SIZE / 2) * 128.0f
        return CameraState(centerX, START_CAMERA_HEIGHT, centerZ - START_CAMERA_BACK_OFFSET,
            (-Math.toRadians(START_CAMERA_PITCH_DEGREES)).toFloat(), 0.0f)
    }
}
