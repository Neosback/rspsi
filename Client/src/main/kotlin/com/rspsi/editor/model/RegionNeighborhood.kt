package com.rspsi.editor.model

import java.util.LinkedHashMap
import java.util.Optional

/**
 * Loaded 3x3 OSRS region neighborhood centered on one editable region.
 *
 * World-coordinate lookups never clamp or fabricate neighboring data. Missing adjacent regions
 * remain absent so blending, stitching, and diagnostics can distinguish unloaded seams from
 * authored terrain.
 */
class RegionNeighborhood(
    center: WorldRegion?,
    loadedRegions: Map<Int, WorldRegion>?,
) {
    private val centerRegionX: Int
    private val centerRegionY: Int
    private val regions: Map<Int, WorldRegion>

    init {
        val safeCenter = center ?: throw NullPointerException("center")
        val safeLoadedRegions =
            loadedRegions ?: throw NullPointerException("loadedRegions")

        centerRegionX = safeCenter.regionX
        centerRegionY = safeCenter.regionY

        val copy = LinkedHashMap<Int, WorldRegion>()
        for (region in safeLoadedRegions.values) {
            if (
                kotlin.math.abs(region.regionX - centerRegionX) > 1 ||
                kotlin.math.abs(region.regionY - centerRegionY) > 1
            ) {
                continue
            }
            copy[region.regionId()] = region
        }
        copy[safeCenter.regionId()] = safeCenter
        regions = java.util.Map.copyOf(copy)
    }

    fun centerRegionX(): Int = centerRegionX

    fun centerRegionY(): Int = centerRegionY

    fun center(): WorldRegion =
        region(centerRegionX, centerRegionY).orElseThrow()

    fun regions(): Map<Int, WorldRegion> = regions

    fun region(
        regionX: Int,
        regionY: Int,
    ): Optional<WorldRegion> =
        Optional.ofNullable(regions[(regionX shl 8) or regionY])

    fun regionAtWorldTile(
        worldX: Int,
        worldY: Int,
    ): Optional<WorldRegion> {
        if (worldX < 0 || worldY < 0) {
            return Optional.empty()
        }
        return region(worldX shr 6, worldY shr 6)
    }

    fun tileAt(
        plane: Int,
        worldX: Int,
        worldY: Int,
    ): Optional<TileSnapshot> =
        tileSource(plane, worldX, worldY).map { it.snapshot }

    fun tileSource(
        plane: Int,
        worldX: Int,
        worldY: Int,
    ): Optional<WorldTileSource> {
        if (plane < 0 || worldX < 0 || worldY < 0) {
            return Optional.empty()
        }
        val region = regionAtWorldTile(worldX, worldY).orElse(null)
            ?: return Optional.empty()
        if (plane >= region.document.planes()) {
            return Optional.empty()
        }
        val tile = region.document.tile(plane, worldX and 63, worldY and 63)
        return Optional.of(WorldTileSource(tile.snapshot(), tile.heightSource()))
    }

    fun mutableTileAt(
        plane: Int,
        worldX: Int,
        worldY: Int,
    ): Optional<Tile> {
        if (plane < 0 || worldX < 0 || worldY < 0) {
            return Optional.empty()
        }
        val region = regionAtWorldTile(worldX, worldY).orElse(null)
            ?: return Optional.empty()
        if (plane >= region.document.planes()) {
            return Optional.empty()
        }
        return Optional.of(region.document.tile(plane, worldX and 63, worldY and 63))
    }

    /**
     * Resolves authored-plane bridge demotion from the same world tile on plane 1, matching
     * [WorldDocument.effectivePlane] without region clamping.
     */
    fun effectivePlane(
        authoredPlane: Int,
        worldX: Int,
        worldY: Int,
    ): Int {
        if (authoredPlane < 0) {
            throw IllegalArgumentException("Authored plane cannot be negative")
        }
        val bridged = tileAt(1, worldX, worldY)
            .map { it.flags() }
            .map { OsrsTileFlags.hasBridge(it) }
            .orElse(false)
        return if (bridged) authoredPlane - 1 else authoredPlane
    }

    fun centerOriginX(): Int =
        centerRegionX * WorldRegion.REGION_SIZE

    fun centerOriginY(): Int =
        centerRegionY * WorldRegion.REGION_SIZE

    fun isCenterWorldTile(
        worldX: Int,
        worldY: Int,
    ): Boolean =
        worldX >= centerOriginX() &&
            worldX < centerOriginX() + WorldRegion.REGION_SIZE &&
            worldY >= centerOriginY() &&
            worldY < centerOriginY() + WorldRegion.REGION_SIZE

    companion object {
        @JvmStatic
        fun from(
            window: WorldRegionWindow?,
            centerRegionX: Int,
            centerRegionY: Int,
        ): RegionNeighborhood {
            val safeWindow = window ?: throw NullPointerException("window")
            val center = safeWindow.region(centerRegionX, centerRegionY)
                .orElseThrow {
                    IllegalArgumentException(
                        "Center region is not loaded: $centerRegionX,$centerRegionY",
                    )
                }
            return RegionNeighborhood(center, safeWindow.regions())
        }
    }
}
