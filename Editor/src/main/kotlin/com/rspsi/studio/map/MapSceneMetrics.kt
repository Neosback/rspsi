package com.rspsi.studio.map

import com.rspsi.editor.render.GpuUploadPlan
import com.rspsi.editor.render.RenderWindowSceneBuilder
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("com.rspsi.studio.map.MapScene")

/**
 * Stage timings of one full or edit build of a map scene, logged at INFO so region-load and
 * edit-rebuild cost is visible in every Studio log (AGENTS.md renderer telemetry).
 */
internal class SceneBuildMetrics(
    private val windowNanos: Long,
    private val packetNanos: Long,
    private val planNanos: Long,
    private val renderSceneNanos: Long,
    private val totalNanos: Long,
    private val plan: GpuUploadPlan,
) {
    fun log(kind: String, regionX: Int, regionY: Int) {
        LOGGER.info(
            "Map scene {} {} , {}: total={} ms, window={} ms, packet={} ms, plan={} ms, " +
                "renderScene={} ms, vertices={}, indices={}, commands={}, textures={}, geometry={} KiB",
            kind, regionX, regionY, totalNanos / NANOS_PER_MS, windowNanos / NANOS_PER_MS,
            packetNanos / NANOS_PER_MS, planNanos / NANOS_PER_MS, renderSceneNanos / NANOS_PER_MS,
            plan.vertices().size, plan.indices().size, plan.commands().size, plan.textures().size,
            geometryKiB(),
        )
    }

    /**
     * Native ZoneVboManager uploads twelve floats per vertex and one int per index. This is
     * the flattened-plan upper bound before unchanged zone allocations are reused on the GPU.
     */
    private fun geometryKiB(): Long {
        val bytes = plan.vertices().size.toLong() * 12L * Float.SIZE_BYTES +
            plan.indices().size.toLong() * Int.SIZE_BYTES
        return (bytes + 1023L) / 1024L
    }
}

/** Costs and reuse counts of one animation refresh. */
internal class AnimationPipelineMetrics(
    val clientCycle: Int,
    val windowTimings: RenderWindowSceneBuilder.AnimationRefreshTimings,
    val windowRefreshNanos: Long,
    val semanticSceneNanos: Long,
    val packetNanos: Long,
    val planNanos: Long,
    val totalNanos: Long,
    val dirtyZones: Int,
    val changedTiles: Int,
    val rebuiltModelTiles: Int,
    val packetUpdated: Boolean,
    val packetRebuiltTiles: Int,
    val packetReusedTiles: Int,
    val planUpdated: Boolean,
    val planRebuiltTiles: Int,
    val planReusedTiles: Int,
    val planRebuiltZones: Int,
    val planReusedZones: Int,
    val heapBeforeBytes: Long,
    val heapAfterBytes: Long,
)

/**
 * Low-overhead rolling diagnostics for the animation hot path. Per-refresh detail stays at
 * DEBUG; an aggregate INFO line every [REPORT_INTERVAL] refreshes reveals sustained CPU or
 * allocation pressure without one log line per animation frame.
 */
internal class AnimationRefreshDiagnostics {
    private var samples = 0
    private var totalNanos = 0L
    private var windowNanos = 0L
    private var paddedWorldNanos = 0L
    private var modelRebuildNanos = 0L
    private var semanticNanos = 0L
    private var packetNanos = 0L
    private var planNanos = 0L
    private var maxTotalNanos = 0L
    private var peakHeapBytes = 0L
    private var changedTiles = 0L
    private var rebuiltModelTiles = 0L
    private var packetRebuiltTiles = 0L
    private var planRebuiltTiles = 0L
    private var planRebuiltZones = 0L

    @Synchronized
    fun record(metric: AnimationPipelineMetrics) {
        val inner = metric.windowTimings
        if (LOGGER.isDebugEnabled) {
            LOGGER.debug(
                "Animation perf cycle={} total={}ms window={}ms (scan={} padded={} models={} detect={}) " +
                    "semantic={}ms packet={}ms plan={}ms dirtyZones={} changedTiles={} rebuiltModelTiles={} " +
                    "packetUpdated={} packetTiles={}/{} planUpdated={} planTiles={}/{} planZones={}/{} " +
                    "heap={}MiB delta={}KiB",
                metric.clientCycle, millis(metric.totalNanos), millis(metric.windowRefreshNanos),
                millis(inner.activeScanNanos()), millis(inner.paddedWorldNanos()),
                millis(inner.modelRebuildNanos()), millis(inner.changeDetectionNanos()),
                millis(metric.semanticSceneNanos), millis(metric.packetNanos), millis(metric.planNanos),
                metric.dirtyZones, metric.changedTiles, metric.rebuiltModelTiles, metric.packetUpdated,
                metric.packetRebuiltTiles, metric.packetReusedTiles, metric.planUpdated,
                metric.planRebuiltTiles, metric.planReusedTiles, metric.planRebuiltZones,
                metric.planReusedZones, mebibytes(metric.heapAfterBytes),
                (metric.heapAfterBytes - metric.heapBeforeBytes) / 1024.0,
            )
        }
        samples++
        totalNanos += metric.totalNanos
        windowNanos += metric.windowRefreshNanos
        paddedWorldNanos += inner.paddedWorldNanos()
        modelRebuildNanos += inner.modelRebuildNanos()
        semanticNanos += metric.semanticSceneNanos
        packetNanos += metric.packetNanos
        planNanos += metric.planNanos
        maxTotalNanos = maxOf(maxTotalNanos, metric.totalNanos)
        peakHeapBytes = maxOf(peakHeapBytes, metric.heapAfterBytes)
        changedTiles += metric.changedTiles
        rebuiltModelTiles += metric.rebuiltModelTiles
        packetRebuiltTiles += metric.packetRebuiltTiles
        planRebuiltTiles += metric.planRebuiltTiles
        planRebuiltZones += metric.planRebuiltZones
        if (samples >= REPORT_INTERVAL) {
            LOGGER.info(
                "Animation perf {} refreshes: avg total={}ms window={}ms (padded={} models={}) " +
                    "semantic={}ms packet={}ms plan={}ms, max={}ms, avgChangedTiles={}, " +
                    "avgRebuiltModelTiles={}, avgPacketRebuiltTiles={}, avgPlanRebuiltTiles={}, " +
                    "avgPlanRebuiltZones={}, peakHeap={}MiB",
                samples, millis(totalNanos / samples), millis(windowNanos / samples),
                millis(paddedWorldNanos / samples), millis(modelRebuildNanos / samples),
                millis(semanticNanos / samples), millis(packetNanos / samples), millis(planNanos / samples),
                millis(maxTotalNanos), changedTiles.toDouble() / samples,
                rebuiltModelTiles.toDouble() / samples, packetRebuiltTiles.toDouble() / samples,
                planRebuiltTiles.toDouble() / samples, planRebuiltZones.toDouble() / samples,
                mebibytes(peakHeapBytes),
            )
            reset()
        }
    }

    private fun reset() {
        samples = 0
        totalNanos = 0L
        windowNanos = 0L
        paddedWorldNanos = 0L
        modelRebuildNanos = 0L
        semanticNanos = 0L
        packetNanos = 0L
        planNanos = 0L
        maxTotalNanos = 0L
        peakHeapBytes = 0L
        changedTiles = 0L
        rebuiltModelTiles = 0L
        packetRebuiltTiles = 0L
        planRebuiltTiles = 0L
        planRebuiltZones = 0L
    }

    private companion object {
        const val REPORT_INTERVAL = 100

        fun millis(nanos: Long): Double = nanos / 1_000_000.0

        fun mebibytes(bytes: Long): Double = bytes / (1024.0 * 1024.0)
    }
}

internal fun logEditRebuild(message: String, vararg args: Any?) = LOGGER.info(message, *args)

internal fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

private const val NANOS_PER_MS = 1_000_000L
