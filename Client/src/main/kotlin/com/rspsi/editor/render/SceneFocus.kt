package com.rspsi.editor.render

import com.rspsi.editor.model.WorldRegion
import com.rspsi.editor.model.WorldRegionWindow
import com.rspsi.editor.model.WorldTileAddress

/**
 * The absolute world-tile rectangle a scene build emits, inclusive on both ends.
 *
 * A [WorldRegionWindow] may hold more regions than the viewport draws: neighbours are
 * loaded so shared edges stitch, underlays blend and contoured objects sample real heights
 * exactly as the client does across a region border. The focus keeps compile work and
 * resident geometry to the active region plus a thin context ring of its neighbours.
 */
@JvmRecord
data class SceneFocus(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY) { "Empty scene focus: $this" }
    }

    fun contains(worldX: Int, worldY: Int): Boolean =
        worldX in minX..maxX && worldY in minY..maxY

    fun contains(address: WorldTileAddress): Boolean = contains(address.worldX, address.worldY)

    fun intersectsRegion(regionX: Int, regionY: Int): Boolean {
        val regionMinX = regionX * WorldRegion.REGION_SIZE
        val regionMinY = regionY * WorldRegion.REGION_SIZE
        return regionMinX <= maxX && regionMinX + WorldRegion.REGION_SIZE - 1 >= minX &&
            regionMinY <= maxY && regionMinY + WorldRegion.REGION_SIZE - 1 >= minY
    }

    /** The overlap with [window]'s tiles, or null when they do not meet. */
    fun clampTo(window: WorldRegionWindow): SceneFocus? {
        val whole = of(window)
        val clampedMinX = maxOf(minX, whole.minX)
        val clampedMinY = maxOf(minY, whole.minY)
        val clampedMaxX = minOf(maxX, whole.maxX)
        val clampedMaxY = minOf(maxY, whole.maxY)
        if (clampedMinX > clampedMaxX || clampedMinY > clampedMaxY) return null
        return SceneFocus(clampedMinX, clampedMinY, clampedMaxX, clampedMaxY)
    }

    companion object {
        /** Tiles of each neighbouring region drawn around the active one: one 8x8 zone. */
        const val CONTEXT_RING_TILES = 8

        /** Every tile of the window. */
        @JvmStatic
        fun of(window: WorldRegionWindow): SceneFocus {
            val world = window.worldWindow()
            return SceneFocus(
                world.originX,
                world.originY,
                world.originX + world.width - 1,
                world.originY + world.length - 1,
            )
        }

        /** One region plus [ring] tiles of each neighbour. */
        @JvmStatic
        fun aroundRegion(regionX: Int, regionY: Int, ring: Int): SceneFocus {
            require(ring >= 0) { "Context ring cannot be negative" }
            val minX = regionX * WorldRegion.REGION_SIZE
            val minY = regionY * WorldRegion.REGION_SIZE
            return SceneFocus(
                minX - ring,
                minY - ring,
                minX + WorldRegion.REGION_SIZE - 1 + ring,
                minY + WorldRegion.REGION_SIZE - 1 + ring,
            )
        }
    }
}
