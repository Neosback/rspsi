package com.rspsi.cache.store

import com.rspsi.cache.worldmap.OsrsWorldMapArea
import dev.openrune.cache.worldmap.worldmap.MapsquareMultiSection
import dev.openrune.cache.worldmap.worldmap.MapsquareSingleSection
import dev.openrune.cache.worldmap.worldmap.WorldMapAreaDetails
import dev.openrune.cache.worldmap.worldmap.ZoneMultiSection
import dev.openrune.cache.worldmap.worldmap.ZoneSingleSection
import io.netty.buffer.Unpooled
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Authentic OSRS cache world map loader.
 *
 * Decodes with OpenRune FileStore's world-map section types, so it lives in the cache
 * adapter boundary; callers only see the neutral [OsrsWorldMapArea] and images.
 *
 * OSRS caches store pre-rendered world map graphics in:
 * - Index 19 (WORLDMAPAREAS):
 *     - Archive 0: Area details & section layout
 *     - Archive 1: Compositemap block & element data
 *     - Archive 2: Composite texture PNGs (e.g. 768x544 for Gielinor Surface)
 * - Index 20 (WORLDMAP_GROUND):
 *     - Archive (rx shl 8) or ry: 64x64 PNG per mapsquare
 */
object OpenRuneWorldMapLoader {

    const val INDEX_WORLDMAP_GEOGRAPHY = 18
    const val INDEX_WORLDMAP_AREAS = 19
    const val INDEX_WORLDMAP_GROUND = 20

    const val ARCHIVE_DETAILS = 0
    const val ARCHIVE_COMPOSITE_MAP = 1
    const val ARCHIVE_COMPOSITE_TEXTURE = 2

    /** Returns true if the cache contains the authentic OSRS world map archives. */
    fun hasWorldMap(store: CacheStore): Boolean {
        val archives19 = store.archiveIds(INDEX_WORLDMAP_AREAS)
        return archives19.isNotEmpty() &&
            archives19.contains(ARCHIVE_DETAILS) &&
            archives19.contains(ARCHIVE_COMPOSITE_TEXTURE)
    }

    /**
     * Loads all world map area definitions from Archive 19, Group 0 (details).
     * Area 0 (main / Gielinor Surface) is listed first, followed by others sorted by display name.
     */
    fun loadAreas(store: CacheStore): List<OsrsWorldMapArea> {
        val fileIds = store.fileIds(INDEX_WORLDMAP_AREAS, ARCHIVE_DETAILS)
        if (fileIds.isEmpty()) return emptyList()

        val areas = mutableListOf<OsrsWorldMapArea>()
        for (fileId in fileIds) {
            val data = store.read(INDEX_WORLDMAP_AREAS, ARCHIVE_DETAILS, fileId) ?: continue
            try {
                val details = WorldMapAreaDetails.decode(fileId, Unpooled.wrappedBuffer(data))
                var minRx = Int.MAX_VALUE
                var maxRx = Int.MIN_VALUE
                var minRy = Int.MAX_VALUE
                var maxRy = Int.MIN_VALUE

                for (section in details.sections) {
                    when (section) {
                        is MapsquareMultiSection -> {
                            minRx = minOf(minRx, section.mapsquareDestinationMinX)
                            maxRx = maxOf(maxRx, section.mapsquareDestinationMaxX)
                            minRy = minOf(minRy, section.mapsquareDestinationMinY)
                            maxRy = maxOf(maxRy, section.mapsquareDestinationMaxY)
                        }
                        is MapsquareSingleSection -> {
                            minRx = minOf(minRx, section.mapsquareDestinationX)
                            maxRx = maxOf(maxRx, section.mapsquareDestinationX)
                            minRy = minOf(minRy, section.mapsquareDestinationY)
                            maxRy = maxOf(maxRy, section.mapsquareDestinationY)
                        }
                        is ZoneMultiSection -> {
                            minRx = minOf(minRx, section.mapsquareDestinationX)
                            maxRx = maxOf(maxRx, section.mapsquareDestinationX)
                            minRy = minOf(minRy, section.mapsquareDestinationY)
                            maxRy = maxOf(maxRy, section.mapsquareDestinationY)
                        }
                        is ZoneSingleSection -> {
                            minRx = minOf(minRx, section.mapsquareDestinationX)
                            maxRx = maxOf(maxRx, section.mapsquareDestinationX)
                            minRy = minOf(minRy, section.mapsquareDestinationY)
                            maxRy = maxOf(maxRy, section.mapsquareDestinationY)
                        }
                    }
                }

                if (minRx == Int.MAX_VALUE) {
                    val originRx = details.origin.x shr 6
                    val originRy = details.origin.y shr 6
                    minRx = originRx
                    maxRx = originRx
                    minRy = originRy
                    maxRy = originRy
                }

                areas += OsrsWorldMapArea(
                    id = fileId,
                    internalName = details.internalName,
                    displayName = details.displayName,
                    originX = details.origin.x,
                    originY = details.origin.y,
                    originPlane = details.origin.level,
                    backgroundColour = details.backgroundColour,
                    isMain = details.isMain,
                    zoom = details.zoom,
                    regionLowX = minRx,
                    regionHighX = maxRx,
                    regionLowY = minRy,
                    regionHighY = maxRy,
                )
            } catch (_: Throwable) {
                // Ignore corrupt or unsupported area files.
            }
        }

        // Put main surface map first, then sort remaining areas by display name.
        return areas.sortedWith(compareByDescending<OsrsWorldMapArea> { it.isMain }.thenBy { it.displayName })
    }

    /**
     * Loads the pre-baked composite texture PNG for [areaId] from Archive 19, Group 2.
     */
    fun loadCompositeTexture(store: CacheStore, areaId: Int): BufferedImage? {
        val data = store.read(INDEX_WORLDMAP_AREAS, ARCHIVE_COMPOSITE_TEXTURE, areaId) ?: return null
        return try {
            ImageIO.read(ByteArrayInputStream(data))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Loads a 64x64 ground PNG image for a single mapsquare from Index 20.
     */
    fun loadRegionGround(store: CacheStore, regionX: Int, regionY: Int): BufferedImage? {
        val squareId = (regionX shl 8) or regionY
        val data = store.read(INDEX_WORLDMAP_GROUND, squareId, 0) ?: return null
        return try {
            ImageIO.read(ByteArrayInputStream(data))
        } catch (_: Throwable) {
            null
        }
    }
}
