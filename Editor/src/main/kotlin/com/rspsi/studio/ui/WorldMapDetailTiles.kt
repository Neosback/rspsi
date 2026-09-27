package com.rspsi.studio.ui

import com.rspsi.cache.definition.DefinitionProvider
import com.rspsi.cache.workspace.OsrsBundle
import com.rspsi.editor.minimap.MinimapBuilder
import com.rspsi.editor.model.WorldRegion
import com.rspsi.editor.model.WorldRegionWindow
import org.lwjgl.opengl.GL11
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Sharp world-map tiles: each region drawn at four pixels per tile by the same shaped-tile
 * minimap renderer Map Studio uses ([MinimapBuilder.buildShaped]).
 *
 * The whole world at that resolution would need ~800 MB, so tiles are made on demand for
 * the regions on screen only, nearest to the view centre first, and a fixed-size LRU
 * ([maxTextures] of 256 KB each) keeps memory bounded however long the user pans.
 * Pixel buffers are dropped once uploaded; only GPU textures are kept.
 *
 * Each tile renders from its region plus a [BORDER]-tile neighbour ring, because the
 * renderer blends underlays across a 5-tile radius and leaves a document's outer ring
 * empty; the ring is cropped off, so tiles meet without seams.
 */
class WorldMapDetailTiles(private val maxTextures: Int = 64) {
    private val builder = MinimapBuilder()

    /** One worker: decoding shares the cache store with the map loader. */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "openrune-world-map-detail").apply { isDaemon = true }
    }

    private class Rendered(val key: Int, val argb: IntArray)

    private val ready = ConcurrentLinkedQueue<Rendered>()
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    private val unavailable = ConcurrentHashMap.newKeySet<Int>()

    /** Keys the view wants this frame; queued work for anything else is skipped. */
    @Volatile
    private var wanted: Set<Int> = emptySet()

    /** Recently decoded regions, shared by neighbouring tiles' border rings. */
    private val decoded = object : LinkedHashMap<Int, WorldRegion>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, WorldRegion>?) = size > DECODED_REGIONS
    }

    private val textures = object : LinkedHashMap<Int, WorldMapRasterTexture>(maxTextures, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, WorldMapRasterTexture>?): Boolean {
            if (size <= maxTextures) return false
            eldest?.value?.dispose()
            return true
        }
    }

    /**
     * Declares the regions on screen, ordered nearest-first, and queues any not yet
     * rendered. Render thread only.
     */
    fun request(bundle: OsrsBundle, definitions: DefinitionProvider, plane: Int, regions: List<Int>) {
        val keys = regions.map { key(plane, it shr 8, it and 0xFF) }
        wanted = keys.toHashSet()
        for (key in keys) {
            if (textures.containsKey(key) || key in unavailable || !inFlight.add(key)) continue
            executor.execute { render(bundle, definitions, key) }
        }
    }

    /** Uploads at most [MAX_UPLOADS_PER_FRAME] finished tiles. Render thread only. */
    fun pumpUpload() {
        repeat(MAX_UPLOADS_PER_FRAME) {
            val tile = ready.poll() ?: return
            val texture = WorldMapRasterTexture(GL11.GL_LINEAR)
            if (texture.upload(tile.argb, TILE_PIXELS, TILE_PIXELS)) {
                texture.releaseStaging()
                textures[tile.key] = texture
            } else {
                texture.dispose()
            }
        }
    }

    /** The GL texture for a region tile, or 0 while it is not ready. Render thread only. */
    fun textureId(plane: Int, regionX: Int, regionY: Int): Int =
        textures[key(plane, regionX, regionY)]?.id ?: 0

    fun clear() {
        wanted = emptySet()
        textures.values.forEach { it.dispose() }
        textures.clear()
        ready.clear()
        inFlight.clear()
        unavailable.clear()
        synchronized(decoded) { decoded.clear() }
    }

    fun dispose() {
        executor.shutdownNow()
        clear()
    }

    private fun render(bundle: OsrsBundle, definitions: DefinitionProvider, key: Int) {
        try {
            if (key !in wanted) return
            val plane = key ushr 16
            val regionX = (key shr 8) and 0xFF
            val regionY = key and 0xFF
            val centre = region(bundle, regionX, regionY)
            if (centre == null || plane >= centre.document.planes()) {
                unavailable += key
                return
            }
            val regions = HashMap<Int, WorldRegion>()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val x = regionX + dx
                    val y = regionY + dy
                    if (x !in 0..255 || y !in 0..255) continue
                    region(bundle, x, y)?.let { regions[it.regionId()] = it }
                }
            }
            val minX = regions.values.minOf { it.regionX }
            val minY = regions.values.minOf { it.regionY }
            val window = WorldRegionWindow(minX, minY, regions.values.maxOf { it.regionX } - minX + 1,
                regions.values.maxOf { it.regionY } - minY + 1, regions)
            // A one-tile empty frame keeps the centre region off the document's outer ring,
            // which the renderer leaves blank, even where a neighbour region does not exist.
            val padded = window.materializePaddedWorldDocument(1)
            val image = builder.buildShaped(padded, plane, definitions)
            // Crop the centre region out of the (up to 3x3) window image. Rows run north to south.
            val offsetX = (1 + (regionX - minX) * WorldRegion.REGION_SIZE) * PIXELS_PER_TILE
            val tilesAbove = 1 + (window.regionHeight() - 1 - (regionY - minY)) * WorldRegion.REGION_SIZE
            val offsetY = tilesAbove * PIXELS_PER_TILE
            val argb = IntArray(TILE_PIXELS * TILE_PIXELS)
            val source = image.argb()
            for (row in 0 until TILE_PIXELS) {
                System.arraycopy(source, (offsetY + row) * image.width() + offsetX, argb, row * TILE_PIXELS, TILE_PIXELS)
            }
            ready += Rendered(key, argb)
        } catch (_: RuntimeException) {
            // A truncated or unreadable archive leaves a hole; the 64 px ground tile shows instead.
            unavailable += key
        } finally {
            inFlight -= key
        }
    }

    /** Decodes one region through the project's map service, reusing recent decodes. */
    private fun region(bundle: OsrsBundle, regionX: Int, regionY: Int): WorldRegion? {
        val id = (regionX shl 8) or regionY
        synchronized(decoded) { decoded[id]?.let { return it } }
        val loaded = try {
            bundle.openWindowAround(regionX, regionY, 0).regions()[id]
        } catch (_: RuntimeException) {
            null
        } ?: return null
        synchronized(decoded) { decoded[id] = loaded }
        return loaded
    }

    private companion object {
        const val PIXELS_PER_TILE = 4
        const val TILE_PIXELS = WorldRegion.REGION_SIZE * PIXELS_PER_TILE
        const val MAX_UPLOADS_PER_FRAME = 4

        /** Decoded regions kept for neighbouring tiles' borders (a 3x3 block plus spares). */
        const val DECODED_REGIONS = 16

        fun key(plane: Int, regionX: Int, regionY: Int): Int = (plane shl 16) or (regionX shl 8) or regionY
    }
}
