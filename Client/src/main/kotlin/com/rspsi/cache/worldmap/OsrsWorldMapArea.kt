package com.rspsi.cache.worldmap

/**
 * Authentic OSRS World Map area metadata decoded from Archive 19, group 0 (details).
 */
data class OsrsWorldMapArea(
    val id: Int,
    val internalName: String,
    val displayName: String,
    val originX: Int,
    val originY: Int,
    val originPlane: Int,
    val backgroundColour: Int,
    val isMain: Boolean,
    val zoom: Int,
    val regionLowX: Int,
    val regionHighX: Int,
    val regionLowY: Int,
    val regionHighY: Int,
) {
    val widthRegions: Int get() = (regionHighX - regionLowX + 1).coerceAtLeast(1)
    val heightRegions: Int get() = (regionHighY - regionLowY + 1).coerceAtLeast(1)
    val originRegionX: Int get() = originX shr 6
    val originRegionY: Int get() = originY shr 6

    fun containsRegion(rx: Int, ry: Int): Boolean =
        rx in regionLowX..regionHighX && ry in regionLowY..regionHighY
}
