package com.rspsi.studio.ui

import com.rspsi.cache.store.CacheStore
import com.rspsi.cache.store.OpenRuneWorldMapLoader
import org.lwjgl.opengl.GL11
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * LRU cache of authentic high-resolution 64x64 mapsquare ground textures decoded from
 * Index 20 (WORLDMAP_GROUND).
 *
 * Background worker threads decode PNG images into ARGB pixel buffers, and the
 * render thread uploads them to OpenGL textures during [pumpUpload].
 */
class WorldMapGroundCache(private val maxCapacity: Int = 128) {

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "openrune-world-map-ground-loader").apply { isDaemon = true }
    }

    private class LoadedTile(val regionKey: Int, val pixels: IntArray)

    private val readyQueue = ConcurrentLinkedQueue<LoadedTile>()
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    private val unavailable = ConcurrentHashMap.newKeySet<Int>()

    private val glTextures = object : LinkedHashMap<Int, WorldMapRasterTexture>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, WorldMapRasterTexture>?): Boolean {
            if (size > maxCapacity) {
                eldest?.value?.dispose()
                inFlight.remove(eldest?.key)
                return true
            }
            return false
        }
    }

    /**
     * Uploads any decoded tile rasters to OpenGL textures. Render thread only.
     */
    fun pumpUpload() {
        while (true) {
            val tile = readyQueue.poll() ?: break
            val existing = glTextures[tile.regionKey]
            val tex = existing ?: WorldMapRasterTexture(GL11.GL_LINEAR)
            tex.upload(tile.pixels, 64, 64)
            glTextures[tile.regionKey] = tex
        }
    }

    /**
     * Returns the GL texture ID for mapsquare [regionX], [regionY] if ready,
     * or 0 if not ready (and enqueues background load if needed).
     * Render thread only.
     */
    fun getTextureId(store: CacheStore, regionX: Int, regionY: Int): Int {
        val key = (regionX shl 8) or regionY
        val existing = glTextures[key]
        if (existing != null && existing.hasContent()) {
            return existing.id
        }
        if (unavailable.contains(key) || !inFlight.add(key)) {
            return 0
        }

        executor.execute {
            try {
                val img = OpenRuneWorldMapLoader.loadRegionGround(store, regionX, regionY)
                if (img != null && img.width == 64 && img.height == 64) {
                    val raster = IntArray(64 * 64)
                    img.getRGB(0, 0, 64, 64, raster, 0, 64)
                    readyQueue.add(LoadedTile(key, raster))
                } else {
                    unavailable.add(key)
                }
            } catch (_: Throwable) {
                unavailable.add(key)
            }
        }
        return 0
    }

    fun clear() {
        glTextures.values.forEach { it.dispose() }
        glTextures.clear()
        inFlight.clear()
        unavailable.clear()
        readyQueue.clear()
    }

    fun dispose() {
        executor.shutdownNow()
        clear()
    }
}
