package com.openrune.studio.core.osrs.map

enum class TerrainHeightSourceKind {
    UNKNOWN,
    GENERATED,
    EXPLICIT,
}

data class TerrainHeightSource(
    val kind: TerrainHeightSourceKind,
    val explicitValue: Int = 0,
) {
    init {
        require(explicitValue in 0..255) {
            "Terrain height value must be between 0 and 255"
        }
        require(kind == TerrainHeightSourceKind.EXPLICIT || explicitValue == 0) {
            "Only explicit terrain heights can carry an explicit value"
        }
    }

    val generated: Boolean
        get() = kind == TerrainHeightSourceKind.GENERATED

    companion object {
        fun generated(): TerrainHeightSource =
            TerrainHeightSource(TerrainHeightSourceKind.GENERATED)

        fun explicit(value: Int): TerrainHeightSource =
            TerrainHeightSource(
                TerrainHeightSourceKind.EXPLICIT,
                if (value == 1) 0 else value,
            )

        fun unknown(): TerrainHeightSource =
            TerrainHeightSource(TerrainHeightSourceKind.UNKNOWN)
    }
}

data class OsrsObjectPlacement(
    val id: Int,
    val type: Int,
    val rotation: Int,
    val plane: Int,
    val x: Int,
    val y: Int,
) {
    init {
        require(id >= 0) { "Object id cannot be negative" }
        require(type in 0..22) { "OSRS location shape must be between 0 and 22" }
        require(rotation in 0..3) { "Object rotation must be between 0 and 3" }
        require(plane in 0 until OsrsRegionData.PLANES) { "Object plane must be between 0 and 3" }
        require(x in 0 until OsrsRegionData.REGION_SIZE) { "Object x must be between 0 and 63" }
        require(y in 0 until OsrsRegionData.REGION_SIZE) { "Object y must be between 0 and 63" }
    }
}

data class OsrsTileData(
    val southWestHeight: Int = 0,
    val southEastHeight: Int = 0,
    val northEastHeight: Int = 0,
    val northWestHeight: Int = 0,
    val underlayId: Int = 0,
    val overlayId: Int = 0,
    val overlayShape: Int = 0,
    val overlayRotation: Int = 0,
    val flags: Int = 0,
    val objects: List<OsrsObjectPlacement> = emptyList(),
    val heightSource: TerrainHeightSource = TerrainHeightSource.unknown(),
) {
    init {
        require(underlayId in 0..255) { "Underlay id must be between 0 and 255" }
        require(overlayId in 0..32767) { "Overlay id must be between 0 and 32767" }
        require(overlayShape in 0..11) { "Overlay shape must be between 0 and 11" }
        require(overlayRotation in 0..3) { "Overlay rotation must be between 0 and 3" }
        require(flags in 0..32) { "Tile flags must be between 0 and 32" }
    }
}

class OsrsRegionData(
    val regionX: Int,
    val regionY: Int,
    tiles: List<OsrsTileData> = List(TILE_COUNT) { OsrsTileData() },
) {
    private val tiles: List<OsrsTileData> = tiles.toList()

    init {
        require(regionX in 0..255 && regionY in 0..255) {
            "OSRS region coordinates must be between 0 and 255"
        }
        require(this.tiles.size == TILE_COUNT) {
            "OSRS region must contain exactly $TILE_COUNT tiles"
        }
    }

    fun regionId(): Int = (regionX shl 8) or regionY

    fun tile(plane: Int, x: Int, y: Int): OsrsTileData {
        require(plane in 0 until PLANES) { "Plane must be between 0 and 3" }
        require(x in 0 until REGION_SIZE) { "x must be between 0 and 63" }
        require(y in 0 until REGION_SIZE) { "y must be between 0 and 63" }
        return tiles[index(plane, x, y)]
    }

    fun tiles(): List<OsrsTileData> = tiles

    companion object {
        const val REGION_SIZE = 64
        const val PLANES = 4
        const val TILE_COUNT = REGION_SIZE * REGION_SIZE * PLANES

        internal fun index(plane: Int, x: Int, y: Int): Int =
            plane * REGION_SIZE * REGION_SIZE + x * REGION_SIZE + y
    }
}
