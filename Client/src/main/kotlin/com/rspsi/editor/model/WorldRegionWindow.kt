package com.rspsi.editor.model

import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Optional
import java.util.TreeSet
import kotlin.math.min

/**
 * A bounded OSRS world window made up of canonical 64x64 regions.
 *
 * Missing regions remain explicit absence rather than fabricated terrain. Derived scene builders
 * can therefore distinguish a real empty region from an unloaded/loading-line hole.
 */
class WorldRegionWindow(
    private val minRegionX: Int,
    private val minRegionY: Int,
    private val regionWidth: Int,
    private val regionHeight: Int,
    regions: Map<Int, WorldRegion>?,
) {
    private val regions: Map<Int, WorldRegion>

    init {
        if (
            minRegionX < 0 ||
            minRegionY < 0 ||
            minRegionX + regionWidth > 256 ||
            minRegionY + regionHeight > 256 ||
            regionWidth <= 0 ||
            regionHeight <= 0
        ) {
            throw IllegalArgumentException("Invalid OSRS region window")
        }

        val safeRegions = regions ?: throw NullPointerException("regions")
        val copy = LinkedHashMap<Int, WorldRegion>()
        for (region in safeRegions.values) {
            if (
                region.regionX < minRegionX ||
                region.regionX >= minRegionX + regionWidth ||
                region.regionY < minRegionY ||
                region.regionY >= minRegionY + regionHeight
            ) {
                throw IllegalArgumentException(
                    "Region lies outside the requested window: ${region.regionId()}",
                )
            }
            if (copy.put(region.regionId(), region) != null) {
                throw IllegalArgumentException("Duplicate region ${region.regionId()}")
            }
        }
        this.regions = java.util.Map.copyOf(copy)
    }

    fun minRegionX(): Int = minRegionX

    fun minRegionY(): Int = minRegionY

    fun regionWidth(): Int = regionWidth

    fun regionHeight(): Int = regionHeight

    fun expectedRegionCount(): Int = regionWidth * regionHeight

    fun loadedRegionCount(): Int = regions.size

    fun complete(): Boolean = loadedRegionCount() == expectedRegionCount()

    fun regions(): Map<Int, WorldRegion> = regions

    /** Returns a deep neutral copy suitable for derived scene preparation. */
    fun copy(): WorldRegionWindow {
        val copy = LinkedHashMap<Int, WorldRegion>()
        for (region in regions.values) {
            copy[region.regionId()] =
                WorldRegion(
                    region.regionX,
                    region.regionY,
                    region.document.copy(),
                )
        }
        return WorldRegionWindow(minRegionX, minRegionY, regionWidth, regionHeight, copy)
    }

    fun worldWindow(): WorldWindow =
        WorldWindow(
            minRegionX * WorldRegion.REGION_SIZE,
            minRegionY * WorldRegion.REGION_SIZE,
            regionWidth * WorldRegion.REGION_SIZE,
            regionHeight * WorldRegion.REGION_SIZE,
        )

    /**
     * Materializes one world-addressed document for derived scene work.
     *
     * Loaded regions are copied into world-relative positions. Missing regions remain neutral
     * empty tiles. This derived document is never the editor's authored source of truth.
     */
    fun materializeWorldDocument(): WorldDocument =
        materializePaddedWorldDocument(0)

    /**
     * Materializes a derived document with an explicit neutral border around the window.
     *
     * Loaded neighboring regions are copied into the border when they are part of this window;
     * absent data remains empty instead of repeating the visible edge.
     */
    fun materializePaddedWorldDocument(border: Int): WorldDocument {
        if (border < 0) {
            throw IllegalArgumentException("World context border cannot be negative")
        }

        val planes =
            regions.values.maxOfOrNull { it.document.planes() }
                ?: WorldDocument.DEFAULT_PLANES
        val world = worldWindow()
        val materialized =
            WorldDocument(
                world.width + border * 2,
                world.length + border * 2,
                planes,
            )

        for (region in regions.values) {
            val offsetX =
                border + (region.regionX - minRegionX) * WorldRegion.REGION_SIZE
            val offsetY =
                border + (region.regionY - minRegionY) * WorldRegion.REGION_SIZE
            val copiedPlanes = min(planes, region.document.planes())

            for (plane in 0 until copiedPlanes) {
                for (x in 0 until WorldRegion.REGION_SIZE) {
                    for (y in 0 until WorldRegion.REGION_SIZE) {
                        val source = region.document.tile(plane, x, y)
                        val destination =
                            materialized.tile(plane, offsetX + x, offsetY + y)
                        val snapshot = source.snapshot()
                        destination.restore(
                            shiftObjects(snapshot, plane, offsetX, offsetY),
                        )
                        destination.heightSource(source.heightSource())
                    }
                }
            }
        }
        return materialized
    }

    fun region(
        regionX: Int,
        regionY: Int,
    ): Optional<WorldRegion> =
        Optional.ofNullable(regions[(regionX shl 8) or regionY])

    fun containsWorldTile(
        worldX: Int,
        worldY: Int,
    ): Boolean {
        val window = worldWindow()
        return worldX >= window.originX &&
            worldX < window.originX + window.width &&
            worldY >= window.originY &&
            worldY < window.originY + window.length
    }

    /** Resolves a loaded canonical tile without inventing data for missing regions. */
    fun tile(
        plane: Int,
        worldX: Int,
        worldY: Int,
    ): Optional<TileSnapshot> =
        tileSource(plane, worldX, worldY).map { it.snapshot }

    /** Resolves a tile while retaining cache height-opcode provenance. */
    fun tileSource(
        plane: Int,
        worldX: Int,
        worldY: Int,
    ): Optional<WorldTileSource> {
        if (plane < 0 || !containsWorldTile(worldX, worldY)) {
            return Optional.empty()
        }

        val regionX = worldX shr 6
        val regionY = worldY shr 6
        val region = regions[(regionX shl 8) or regionY]
            ?: return Optional.empty()
        if (plane >= region.document.planes()) {
            return Optional.empty()
        }

        val tile =
            region.document.tile(
                plane,
                worldX and 63,
                worldY and 63,
            )
        return Optional.of(
            WorldTileSource(tile.snapshot(), tile.heightSource()),
        )
    }

    fun missingRegionIds(): Set<Int> {
        val missing = TreeSet<Int>()
        for (x in minRegionX until minRegionX + regionWidth) {
            for (y in minRegionY until minRegionY + regionHeight) {
                val id = (x shl 8) or y
                if (!regions.containsKey(id)) {
                    missing.add(id)
                }
            }
        }
        return java.util.Set.copyOf(missing)
    }

    /**
     * Compares shared corner heights between adjacent loaded regions.
     *
     * Missing regions are skipped because their boundary cannot be verified yet.
     */
    fun boundaryMismatches(): List<RegionBoundaryMismatch> {
        val mismatches = ArrayList<RegionBoundaryMismatch>()
        for (region in regions.values) {
            val east =
                regions[((region.regionX + 1) shl 8) or region.regionY]
            if (east != null) {
                compareEast(region, east, mismatches)
            }

            val north =
                regions[(region.regionX shl 8) or (region.regionY + 1)]
            if (north != null) {
                compareNorth(region, north, mismatches)
            }
        }
        return java.util.List.copyOf(mismatches)
    }

    /**
     * Materializes shared border vertices from neighboring region origins.
     *
     * A terrain archive stores the origin height for each tile; the final row and column of a
     * standalone 64x64 document are provisional until neighboring regions are present.
     *
     * @return number of tile snapshots updated
     */
    fun stitchSharedEdges(): Int {
        var updates = 0
        for (region in regions.values) {
            val east =
                regions[((region.regionX + 1) shl 8) or region.regionY]
            if (east != null) {
                val northEast =
                    regions[((region.regionX + 1) shl 8) or (region.regionY + 1)]
                updates += stitchEast(region, east, northEast)
            }

            val north =
                regions[(region.regionX shl 8) or (region.regionY + 1)]
            if (north != null) {
                val northEast =
                    regions[((region.regionX + 1) shl 8) or (region.regionY + 1)]
                updates += stitchNorth(region, north, northEast)
            }
        }
        return updates
    }

    private fun shiftObjects(
        source: TileSnapshot,
        plane: Int,
        offsetX: Int,
        offsetY: Int,
    ): TileSnapshot {
        val objects = ArrayList<WorldObject>(source.objects().size)
        for (objectPlacement in source.objects()) {
            objects.add(
                WorldObject(
                    objectPlacement.id,
                    objectPlacement.type,
                    objectPlacement.rotation,
                    plane,
                    objectPlacement.x + offsetX,
                    objectPlacement.y + offsetY,
                ),
            )
        }

        return TileSnapshot(
            source.southWestHeight(),
            source.southEastHeight(),
            source.northEastHeight(),
            source.northWestHeight(),
            source.underlayId(),
            source.overlayId(),
            source.overlayShape(),
            source.overlayRotation(),
            source.flags(),
            objects,
        )
    }

    private fun stitchEast(
        west: WorldRegion,
        east: WorldRegion,
        northEast: WorldRegion?,
    ): Int {
        var updates = 0
        val planes = min(west.document.planes(), east.document.planes())
        for (plane in 0 until planes) {
            for (y in 0 until WorldRegion.REGION_SIZE) {
                val right = east.document.tile(plane, 0, y).snapshot()
                val northWestHeight =
                    if (y < WorldRegion.REGION_SIZE - 1) {
                        east.document.tile(plane, 0, y + 1)
                            .snapshot()
                            .southWestHeight()
                    } else if (northEast == null) {
                        right.northWestHeight()
                    } else {
                        northEast.document.tile(plane, 0, 0)
                            .snapshot()
                            .southWestHeight()
                    }

                val left = west.document.tile(plane, 63, y).snapshot()
                val updated =
                    TileSnapshot(
                        left.southWestHeight(),
                        right.southWestHeight(),
                        northWestHeight,
                        left.northWestHeight(),
                        left.underlayId(),
                        left.overlayId(),
                        left.overlayShape(),
                        left.overlayRotation(),
                        left.flags(),
                        left.objects(),
                    )
                if (updated != left) {
                    west.document.tile(plane, 63, y).restore(updated)
                    updates++
                }
            }
        }
        return updates
    }

    private fun stitchNorth(
        south: WorldRegion,
        north: WorldRegion,
        northEast: WorldRegion?,
    ): Int {
        var updates = 0
        val planes = min(south.document.planes(), north.document.planes())
        for (plane in 0 until planes) {
            for (x in 0 until WorldRegion.REGION_SIZE) {
                val upper = north.document.tile(plane, x, 0).snapshot()
                val northEastHeight =
                    if (x < WorldRegion.REGION_SIZE - 1) {
                        north.document.tile(plane, x + 1, 0)
                            .snapshot()
                            .southWestHeight()
                    } else if (northEast == null) {
                        upper.southEastHeight()
                    } else {
                        northEast.document.tile(plane, 0, 0)
                            .snapshot()
                            .southWestHeight()
                    }

                val lower = south.document.tile(plane, x, 63).snapshot()
                val updated =
                    TileSnapshot(
                        lower.southWestHeight(),
                        lower.southEastHeight(),
                        northEastHeight,
                        upper.southWestHeight(),
                        lower.underlayId(),
                        lower.overlayId(),
                        lower.overlayShape(),
                        lower.overlayRotation(),
                        lower.flags(),
                        lower.objects(),
                    )
                if (updated != lower) {
                    south.document.tile(plane, x, 63).restore(updated)
                    updates++
                }
            }
        }
        return updates
    }

    private fun compareEast(
        west: WorldRegion,
        east: WorldRegion,
        mismatches: MutableList<RegionBoundaryMismatch>,
    ) {
        val planes = min(west.document.planes(), east.document.planes())
        for (plane in 0 until planes) {
            for (along in 0 until WorldRegion.REGION_SIZE) {
                val left = west.document.tile(plane, 63, along).snapshot()
                val right = east.document.tile(plane, 0, along).snapshot()
                compare(
                    west,
                    RegionBoundaryDirection.EAST,
                    plane,
                    along,
                    false,
                    left.southEastHeight(),
                    right.southWestHeight(),
                    mismatches,
                )
                compare(
                    west,
                    RegionBoundaryDirection.EAST,
                    plane,
                    along,
                    true,
                    left.northEastHeight(),
                    right.northWestHeight(),
                    mismatches,
                )
            }
        }
    }

    private fun compareNorth(
        south: WorldRegion,
        north: WorldRegion,
        mismatches: MutableList<RegionBoundaryMismatch>,
    ) {
        val planes = min(south.document.planes(), north.document.planes())
        for (plane in 0 until planes) {
            for (along in 0 until WorldRegion.REGION_SIZE) {
                val lower = south.document.tile(plane, along, 63).snapshot()
                val upper = north.document.tile(plane, along, 0).snapshot()
                compare(
                    south,
                    RegionBoundaryDirection.NORTH,
                    plane,
                    along,
                    false,
                    lower.northWestHeight(),
                    upper.southWestHeight(),
                    mismatches,
                )
                compare(
                    south,
                    RegionBoundaryDirection.NORTH,
                    plane,
                    along,
                    true,
                    lower.northEastHeight(),
                    upper.southEastHeight(),
                    mismatches,
                )
            }
        }
    }

    private fun compare(
        origin: WorldRegion,
        direction: RegionBoundaryDirection,
        plane: Int,
        along: Int,
        upperCorner: Boolean,
        expected: Int,
        actual: Int,
        mismatches: MutableList<RegionBoundaryMismatch>,
    ) {
        if (expected != actual) {
            mismatches.add(
                RegionBoundaryMismatch(
                    direction,
                    plane,
                    origin.regionX,
                    origin.regionY,
                    along,
                    upperCorner,
                    expected,
                    actual,
                ),
            )
        }
    }
}
