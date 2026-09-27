package com.rspsi.studio.ui

import com.rspsi.cache.definition.DefinitionProvider
import com.rspsi.cache.map.MapIndexEntry
import com.rspsi.cache.map.OsrsMapService
import com.rspsi.cache.map.OsrsRegionDecoder
import com.rspsi.cache.store.CacheStore
import com.rspsi.cache.worldmap.OsrsWorldMapArea
import com.rspsi.cache.store.OpenRuneWorldMapLoader
import com.rspsi.editor.minimap.MinimapBuilder
import org.lwjgl.opengl.GL11
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Cache-wide terrain overview for the World Map workspace.
 *
 * For authentic OSRS caches, the overview loads the official composite texture
 * from Index 19 (<10ms) and provides multi-area selection (Gielinor Surface,
 * Zanaris, Mor Ul Rek, undergrounds, etc.) as well as high-resolution 64x64
 * mapsquare tiles from Index 20 when zoomed in.
 *
 * For legacy/custom caches lacking Index 19, it falls back to the asynchronous
 * procedural [MinimapBuilder] bake.
 */
class WorldMapOverview {

    private val builder = MinimapBuilder()
    private val texture = WorldMapRasterTexture(GL11.GL_LINEAR)
    val groundCache = WorldMapGroundCache()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "openrune-world-map-overview").apply { isDaemon = true }
    }

    @Volatile
    private var activeStore: CacheStore? = null

    @Volatile
    private var cachedAreas: List<OsrsWorldMapArea> = emptyList()

    @Volatile
    private var selectedArea: OsrsWorldMapArea? = null

    @Volatile
    private var geometry: Geometry? = null

    @Volatile
    private var pixels: IntArray? = null

    @Volatile
    private var published = -1

    @Volatile
    private var baked = 0

    @Volatile
    private var total = 0

    @Volatile
    private var skipped = 0

    @Volatile
    private var finished = false

    @Volatile
    private var failure: String? = null

    @Volatile
    private var active: Job? = null

    private val jobLock = Any()
    private var queued: Job? = null
    private var running = false
    private var currentKey: String? = null
    private var uploaded = -1
    private var lastUploadMillis = 0L

    val areas: List<OsrsWorldMapArea> get() = cachedAreas

    val currentArea: OsrsWorldMapArea? get() = selectedArea

    fun isAuthentic(): Boolean = cachedAreas.isNotEmpty()

    fun building(): Boolean = !finished

    fun hasRaster(): Boolean = texture.hasContent()

    fun textureId(): Int = texture.id

    /** World tiles represented by one raster texel. */
    fun tilesPerPixel(): Int = geometry?.step ?: 1

    fun originRegionX(): Int = geometry?.regionX ?: 0

    fun originRegionY(): Int = geometry?.regionY ?: 0

    fun rasterWidth(): Int = geometry?.width ?: 0

    fun rasterHeight(): Int = geometry?.height ?: 0

    fun completed(): Int = baked

    fun regionTotal(): Int = total

    /** Returns why the bake stopped early, or null while it is healthy. */
    fun failureText(): String? = failure

    fun statusText(): String {
        val area = selectedArea
        if (cachedAreas.isNotEmpty() && area != null) {
            return "${area.displayName} (${area.widthRegions}x${area.heightRegions} regions)  ·  Authentic OSRS World Map"
        }
        val current = geometry ?: return "Preparing world map"
        if (finished) {
            val unreadable = if (skipped == 0) "" else ", $skipped unreadable"
            return "World map ${current.width}x${current.height} px" +
                "  ·  ${current.step} tiles/px  ·  $total regions$unreadable"
        }
        return "Rendering world map $baked/$total regions"
    }

    fun getGroundTexture(regionX: Int, regionY: Int): Int {
        val store = activeStore ?: return 0
        return groundCache.getTextureId(store, regionX, regionY)
    }

    /**
     * Starts world map loading for [key] using authentic OSRS cache data if available,
     * or procedurally bakes the world map via [MinimapBuilder] as fallback.
     */
    fun request(
        key: String,
        store: CacheStore?,
        entries: List<MapIndexEntry>,
        maps: OsrsMapService,
        definitions: DefinitionProvider,
        focusRegionX: Int,
        focusRegionY: Int,
    ) {
        if (currentKey == key) return
        activeStore = store

        if (store != null && OpenRuneWorldMapLoader.hasWorldMap(store)) {
            try {
                val loaded = OpenRuneWorldMapLoader.loadAreas(store)
                if (loaded.isNotEmpty()) {
                    currentKey = key
                    cachedAreas = loaded
                    val initial = loaded.find { it.containsRegion(focusRegionX, focusRegionY) }
                        ?: loaded.firstOrNull { it.isMain }
                        ?: loaded.first()
                    loadArea(store, initial)
                    return
                }
            } catch (_: Throwable) {
                // Fall through to procedural bake if loading authentic map fails
            }
        }

        if (entries.isEmpty()) {
            failure = "the cache index lists no map regions"
            finished = true
            return
        }
        synchronized(jobLock) {
            currentKey = key
            queued = Job(key, entries, maps, definitions, focusRegionX, focusRegionY)
            startQueued()
        }
    }

    fun switchArea(area: OsrsWorldMapArea) {
        val store = activeStore ?: return
        loadArea(store, area)
    }

    private fun loadArea(store: CacheStore, area: OsrsWorldMapArea) {
        selectedArea = area
        val img = OpenRuneWorldMapLoader.loadCompositeTexture(store, area.id)
        if (img == null) {
            failure = "Failed to load composite texture for area: ${area.displayName}"
            finished = true
            return
        }
        val w = img.width
        val h = img.height
        val raster = IntArray(w * h)
        img.getRGB(0, 0, w, h, raster, 0, w)
        val spanTilesX = area.widthRegions * REGION_TILES
        val step = (spanTilesX / w).coerceAtLeast(1)
        geometry = Geometry(area.regionLowX, area.regionLowY, step, w, h)
        pixels = raster
        total = 1
        baked = 1
        skipped = 0
        published = 1
        uploaded = -1
        finished = true
        failure = null
    }

    /** Uploads the newest published pixels. Render thread only. */
    fun pumpUpload() {
        groundCache.pumpUpload()
        val current = geometry ?: return
        val source = pixels ?: return
        val done = published
        if (done <= uploaded) return
        val now = System.currentTimeMillis()
        if (!finished) {
            if (now - lastUploadMillis < UPLOAD_INTERVAL_MILLIS) return
            if (uploaded >= 0 && done - uploaded < MIN_REGIONS_PER_UPLOAD) return
        }
        if (texture.upload(source, current.width, current.height)) {
            uploaded = done
            lastUploadMillis = now
        }
    }

    /** Forgets the current raster so the next request rebuilds it. */
    fun reset() {
        synchronized(jobLock) {
            currentKey = null
            queued = null
        }
        active = null
        geometry = null
        pixels = null
        published = -1
        uploaded = -1
        baked = 0
        total = 0
        skipped = 0
        finished = false
        failure = null
        activeStore = null
        cachedAreas = emptyList()
        selectedArea = null
        groundCache.clear()
    }

    fun dispose() {
        executor.shutdownNow()
        synchronized(jobLock) {
            currentKey = null
            queued = null
        }
        active = null
        texture.dispose()
        groundCache.dispose()
    }

    private fun startQueued() {
        if (running) return
        val job = queued ?: return
        queued = null
        running = true
        active = job
        executor.execute {
            try {
                bake(job)
            } catch (error: Throwable) {
                failure = error.toString()
                finished = true
            } finally {
                active = null
                synchronized(jobLock) {
                    running = false
                    startQueued()
                }
            }
        }
    }

    private fun bake(job: Job) {
        val usable = job.entries.filter { entry ->
            entry.regionX() in 0..255 && entry.regionY() in 0..255
        }
        val bounds = bounds(usable)
        if (bounds == null) {
            failure = "the cache index lists no usable region coordinates"
            finished = true
            return
        }
        val spanTilesX = (bounds.maxX - bounds.minX + 1) * REGION_TILES
        val spanTilesY = (bounds.maxY - bounds.minY + 1) * REGION_TILES
        val step = stepFor(max(spanTilesX, spanTilesY))
        val current = Geometry(
            bounds.minX,
            bounds.minY,
            step,
            (spanTilesX + step - 1) / step,
            (spanTilesY + step - 1) / step,
        )
        geometry = current
        val raster = IntArray(current.width * current.height)
        pixels = raster

        total = usable.size
        baked = 0
        skipped = 0
        published = 0
        finished = false
        failure = null
        uploaded = -1

        val ordered = usable.sortedBy { entry ->
            val dx = entry.regionX() - job.focusRegionX
            val dy = entry.regionY() - job.focusRegionY
            dx * dx + dy * dy
        }
        for (entry in ordered) {
            if (active !== job) return
            rasterize(job, entry, raster, current)
            baked++
            published = baked
        }
        finished = true
        published = baked
    }

    /**
     * Bakes one region into the overview, counting it as unreadable on failure.
     *
     * A handful of archives in a live cache are truncated or use a layout this
     * decoder does not accept. One of those must leave a hole, not end the bake:
     * the overview is a navigation surface, and 2900 good regions still describe
     * the world.
     */
    private fun rasterize(job: Job, entry: MapIndexEntry, raster: IntArray, current: Geometry) {
        val regionX = entry.regionX()
        val regionY = entry.regionY()
        try {
            val landscape = job.maps.readLandscape(regionX, regionY) ?: return
            if (landscape.isEmpty()) return
            val document = OsrsRegionDecoder.decodeTerrain(
                landscape,
                regionX,
                regionY,
                FLAT_BASE_HEIGHT,
                job.maps.newTerrainFormat(),
            )
            val argb = builder.build(document, PLANE, job.definitions, true).argb()
            blit(argb, raster, current, regionX, regionY)
        } catch (_: RuntimeException) {
            skipped++
        }
    }

    /**
     * Copies one region's 64x64 raster into the overview.
     *
     * Only tiles that land on the raster grid are written. [Geometry.step] is
     * always a power of two dividing [REGION_TILES], so every cell is covered by
     * exactly one tile and region edges never fall between cells.
     */
    private fun blit(
        argb: IntArray,
        raster: IntArray,
        current: Geometry,
        regionX: Int,
        regionY: Int,
    ) {
        val originTileX = current.regionX * REGION_TILES
        val originTileY = current.regionY * REGION_TILES
        val columnBase = (regionX * REGION_TILES - originTileX) / current.step
        val mask = current.step - 1
        if (current.step == 1) {
            for (tileY in 0 until REGION_TILES) {
                val row = current.height - 1 - (regionY * REGION_TILES + tileY - originTileY)
                System.arraycopy(
                    argb,
                    tileY * REGION_TILES,
                    raster,
                    row * current.width + columnBase,
                    REGION_TILES,
                )
            }
            return
        }
        for (tileY in 0 until REGION_TILES) {
            val offsetY = regionY * REGION_TILES + tileY - originTileY
            if (offsetY and mask != 0) continue
            var target = (current.height - 1 - offsetY / current.step) * current.width + columnBase
            val source = tileY * REGION_TILES
            for (tileX in 0 until REGION_TILES) {
                val offsetX = regionX * REGION_TILES + tileX - originTileX
                if (offsetX and mask == 0) raster[target++] = argb[source + tileX]
            }
        }
    }

    private fun bounds(entries: List<MapIndexEntry>): Bounds? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (entry in entries) {
            val regionX = entry.regionX()
            val regionY = entry.regionY()
            if (regionX !in 0..255 || regionY !in 0..255) continue
            if (regionX < minX) minX = regionX
            if (regionX > maxX) maxX = regionX
            if (regionY < minY) minY = regionY
            if (regionY > maxY) maxY = regionY
        }
        if (minX > maxX || minY > maxY) return null
        return Bounds(minX, minY, maxX, maxY)
    }

    /**
     * Picks a power-of-two tile stride so the overview fits [MAX_DIMENSION].
     *
     * Power of two matters: it always divides a 64-tile region, which keeps
     * [blit]'s grid alignment exact.
     */
    private fun stepFor(spanTiles: Int): Int {
        var step = 1
        while (spanTiles / step > MAX_DIMENSION) step = step shl 1
        return step
    }

    private class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    private class Geometry(
        val regionX: Int,
        val regionY: Int,
        val step: Int,
        val width: Int,
        val height: Int,
    )

    private class Job(
        val key: String,
        val entries: List<MapIndexEntry>,
        val maps: OsrsMapService,
        val definitions: DefinitionProvider,
        val focusRegionX: Int,
        val focusRegionY: Int,
    )

    private companion object {
        const val PLANE = 0
        const val REGION_TILES = 64

        /**
         * Caps the overview at roughly 16 MiB of texels. This is a downsampled
         * navigation surface, not a rendering target, so a larger raster only
         * delays the first painted frame.
         */
        const val MAX_DIMENSION = 2048
        const val UPLOAD_INTERVAL_MILLIS = 300L
        const val MIN_REGIONS_PER_UPLOAD = 8

        /**
         * Overview rasters are colour-only: [MinimapBuilder] never reads vertex
         * heights, so opcode-0 tiles decode flat instead of running the
         * deterministic smooth-noise generator once per tile.
         */
        val FLAT_BASE_HEIGHT = OsrsRegionDecoder.BaseHeightProvider { _, _ -> 0 }
    }
}
