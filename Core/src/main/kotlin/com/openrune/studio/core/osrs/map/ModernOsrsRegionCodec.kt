package com.openrune.studio.core.osrs.map

import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

object ModernOsrsRegionCodec {
    fun interface BaseHeightProvider {
        fun heightAt(worldX: Int, worldY: Int): Int
    }

    fun decode(
        landscape: ByteArray,
        locations: ByteArray?,
        regionX: Int,
        regionY: Int,
        baseHeightProvider: BaseHeightProvider =
            BaseHeightProvider(::defaultBaseHeightAtWorldNoiseCoordinate),
    ): OsrsRegionData {
        val terrain = decodeTerrain(landscape, regionX, regionY, baseHeightProvider)
        val objects = decodeLocations(locations ?: ByteArray(0))
        if (objects.isEmpty()) {
            return terrain
        }

        val tiles = terrain.tiles().toMutableList()
        for (objectPlacement in objects) {
            val index =
                OsrsRegionData.index(
                    objectPlacement.plane,
                    objectPlacement.x,
                    objectPlacement.y,
                )
            val tile = tiles[index]
            tiles[index] = tile.copy(objects = tile.objects + objectPlacement)
        }
        return OsrsRegionData(regionX, regionY, tiles)
    }

    fun decodeTerrain(
        data: ByteArray,
        regionX: Int,
        regionY: Int,
        baseHeightProvider: BaseHeightProvider =
            BaseHeightProvider(::defaultBaseHeightAtWorldNoiseCoordinate),
    ): OsrsRegionData {
        val cursor = Cursor(data)
        val heights =
            Array(OsrsRegionData.PLANES) {
                Array(OsrsRegionData.REGION_SIZE + 1) {
                    IntArray(OsrsRegionData.REGION_SIZE + 1)
                }
            }
        val underlays = cube()
        val overlays = cube()
        val shapes = cube()
        val rotations = cube()
        val flags = cube()
        val heightSources =
            Array(OsrsRegionData.PLANES) {
                Array(OsrsRegionData.REGION_SIZE) {
                    arrayOfNulls<TerrainHeightSource>(OsrsRegionData.REGION_SIZE)
                }
            }

        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    decodeTile(
                        cursor = cursor,
                        plane = plane,
                        x = x,
                        y = y,
                        regionX = regionX,
                        regionY = regionY,
                        baseHeightProvider = baseHeightProvider,
                        heights = heights,
                        underlays = underlays,
                        overlays = overlays,
                        shapes = shapes,
                        rotations = rotations,
                        flags = flags,
                        heightSources = heightSources,
                    )
                }
            }
        }

        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                heights[plane][x][OsrsRegionData.REGION_SIZE] =
                    heights[plane][x][OsrsRegionData.REGION_SIZE - 1]
            }
            for (y in 0..OsrsRegionData.REGION_SIZE) {
                heights[plane][OsrsRegionData.REGION_SIZE][y] =
                    heights[plane][OsrsRegionData.REGION_SIZE - 1][y]
            }
        }

        val tiles = MutableList(OsrsRegionData.TILE_COUNT) { OsrsTileData() }
        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    tiles[OsrsRegionData.index(plane, x, y)] =
                        OsrsTileData(
                            southWestHeight = heights[plane][x][y],
                            southEastHeight = heights[plane][x + 1][y],
                            northEastHeight = heights[plane][x + 1][y + 1],
                            northWestHeight = heights[plane][x][y + 1],
                            underlayId = underlays[plane][x][y],
                            overlayId = overlays[plane][x][y],
                            overlayShape = shapes[plane][x][y],
                            overlayRotation = rotations[plane][x][y],
                            flags = flags[plane][x][y],
                            heightSource =
                                heightSources[plane][x][y]
                                    ?: TerrainHeightSource.unknown(),
                        )
                }
            }
        }

        return OsrsRegionData(regionX, regionY, tiles)
    }

    fun decodeLocations(data: ByteArray): List<OsrsObjectPlacement> {
        val cursor = Cursor(data)
        val objects = mutableListOf<OsrsObjectPlacement>()
        var objectId = -1

        while (cursor.remaining() > 0) {
            val objectDelta = readIncrementalSmart(cursor)
            if (objectDelta == 0) {
                break
            }
            objectId += objectDelta
            var packedPosition = 0

            while (cursor.remaining() > 0) {
                val positionDelta = readUnsignedSmart(cursor)
                if (positionDelta == 0) {
                    break
                }
                packedPosition += positionDelta - 1
                val attributes = cursor.readUnsignedByte()
                objects +=
                    OsrsObjectPlacement(
                        id = objectId,
                        type = attributes ushr 2,
                        rotation = attributes and 0x3,
                        plane = (packedPosition ushr 12) and 0x3,
                        x = (packedPosition ushr 6) and 0x3F,
                        y = packedPosition and 0x3F,
                    )
            }
        }

        return objects.toList()
    }

    fun encodeTerrain(region: OsrsRegionData): ByteArray {
        requireSharedHeights(region)
        val out = ByteArrayOutputStream()

        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    val tile = region.tile(plane, x, y)

                    if (tile.overlayId != 0) {
                        writeUnsignedShort(out, 2 + tile.overlayShape * 4 + tile.overlayRotation)
                        writeUnsignedShort(out, tile.overlayId)
                    } else if (tile.overlayShape != 0 || tile.overlayRotation != 0) {
                        writeUnsignedShort(out, 2 + tile.overlayShape * 4 + tile.overlayRotation)
                        writeUnsignedShort(out, 0)
                    }

                    if (tile.flags != 0) {
                        writeUnsignedShort(out, 49 + tile.flags)
                    }

                    if (tile.underlayId != 0) {
                        writeUnsignedShort(out, 81 + tile.underlayId)
                    }

                    val value = heightValue(region, plane, x, y, tile.southWestHeight)
                    require(value != 1) {
                        "Tile $plane,$x,$y requires terrain height byte 1, which OSRS decodes as 0"
                    }
                    writeUnsignedShort(out, 1)
                    out.write(value)
                }
            }
        }

        return out.toByteArray()
    }

    fun encodeLocations(region: OsrsRegionData): ByteArray {
        val objects = mutableListOf<OsrsObjectPlacement>()
        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    for (objectPlacement in region.tile(plane, x, y).objects) {
                        require(
                            objectPlacement.plane == plane &&
                                objectPlacement.x == x &&
                                objectPlacement.y == y,
                        ) {
                            "Object is not owned by its region tile: $objectPlacement"
                        }
                        objects += objectPlacement
                    }
                }
            }
        }

        objects.sortWith(
            compareBy<OsrsObjectPlacement> { it.id }
                .thenBy(::packedPosition)
                .thenBy { it.type shl 2 or it.rotation },
        )

        val out = ByteArrayOutputStream()
        var previousId = -1
        var index = 0
        while (index < objects.size) {
            val objectId = objects[index].id
            writeIncrementalSmart(out, objectId - previousId)
            previousId = objectId
            var previousPosition = 0

            while (index < objects.size && objects[index].id == objectId) {
                val objectPlacement = objects[index++]
                val position = packedPosition(objectPlacement)
                writeUnsignedSmart(out, position - previousPosition + 1)
                out.write(objectPlacement.type shl 2 or objectPlacement.rotation)
                previousPosition = position
            }
            writeUnsignedSmart(out, 0)
        }
        writeUnsignedSmart(out, 0)
        return out.toByteArray()
    }

    fun generatedHeightAtWorldNoiseCoordinate(worldX: Int, worldY: Int): Int =
        -defaultBaseHeightAtWorldNoiseCoordinate(worldX, worldY) * 8

    private fun decodeTile(
        cursor: Cursor,
        plane: Int,
        x: Int,
        y: Int,
        regionX: Int,
        regionY: Int,
        baseHeightProvider: BaseHeightProvider,
        heights: Array<Array<IntArray>>,
        underlays: Array<Array<IntArray>>,
        overlays: Array<Array<IntArray>>,
        shapes: Array<Array<IntArray>>,
        rotations: Array<Array<IntArray>>,
        flags: Array<Array<IntArray>>,
        heightSources: Array<Array<Array<TerrainHeightSource?>>>,
    ) {
        while (true) {
            val opcode = cursor.readUnsignedShort()
            when {
                opcode == 0 || opcode == 1 -> {
                    val value = if (opcode == 1) cursor.readUnsignedByte() else 0
                    heightSources[plane][x][y] =
                        if (opcode == 0) {
                            TerrainHeightSource.generated()
                        } else {
                            TerrainHeightSource.explicit(value)
                        }

                    heights[plane][x][y] =
                        if (plane == 0) {
                            if (opcode == 0) {
                                -baseHeightProvider.heightAt(
                                    regionX * OsrsRegionData.REGION_SIZE + x,
                                    regionY * OsrsRegionData.REGION_SIZE + y,
                                ) * 8
                            } else {
                                -normaliseExplicitHeight(value) * 8
                            }
                        } else {
                            val previous = heights[plane - 1][x][y]
                            if (opcode == 0) {
                                previous - 240
                            } else {
                                previous - normaliseExplicitHeight(value) * 8
                            }
                        }
                    return
                }

                opcode <= 49 -> {
                    val rawOverlay = cursor.readUnsignedShort()
                    overlays[plane][x][y] = rawOverlay and 0x7FFF
                    shapes[plane][x][y] = (opcode - 2) ushr 2
                    rotations[plane][x][y] = (opcode - 2) and 0x3
                }

                opcode <= 81 -> flags[plane][x][y] = opcode - 49
                else -> underlays[plane][x][y] = opcode - 81 and 0xFF
            }
        }
    }

    private fun heightValue(
        region: OsrsRegionData,
        plane: Int,
        x: Int,
        y: Int,
        height: Int,
    ): Int {
        require(height and 7 == 0) { "Tile height must be divisible by 8: $height" }
        val value =
            if (plane == 0) {
                -height / 8
            } else {
                val previous = region.tile(plane - 1, x, y).southWestHeight
                (previous - height) / 8
            }
        require(value in 0..255) { "Height value must be between 0 and 255: $value" }
        return value
    }

    private fun requireSharedHeights(region: OsrsRegionData) {
        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    val tile = region.tile(plane, x, y)
                    if (x + 1 < OsrsRegionData.REGION_SIZE) {
                        val east = region.tile(plane, x + 1, y)
                        require(
                            tile.southEastHeight == east.southWestHeight &&
                                tile.northEastHeight == east.northWestHeight,
                        ) {
                            "Terrain east edge heights do not match at $plane,$x,$y"
                        }
                    }
                    if (y + 1 < OsrsRegionData.REGION_SIZE) {
                        val north = region.tile(plane, x, y + 1)
                        require(
                            tile.northWestHeight == north.southWestHeight &&
                                tile.northEastHeight == north.southEastHeight,
                        ) {
                            "Terrain north edge heights do not match at $plane,$x,$y"
                        }
                    }
                }
            }
        }
    }

    private fun readIncrementalSmart(cursor: Cursor): Int {
        var total = 0
        var value: Int
        do {
            value = readUnsignedSmart(cursor)
            total += value
        } while (value == 32767)
        return total
    }

    private fun readUnsignedSmart(cursor: Cursor): Int =
        if (cursor.peekUnsignedByte() < 128) {
            cursor.readUnsignedByte()
        } else {
            cursor.readUnsignedShort() - 0x8000
        }

    private fun writeIncrementalSmart(out: ByteArrayOutputStream, value: Int) {
        require(value > 0) { "Object IDs must be strictly increasing and non-negative" }
        var remaining = value
        while (remaining >= 32767) {
            writeUnsignedSmart(out, 32767)
            remaining -= 32767
        }
        writeUnsignedSmart(out, remaining)
    }

    private fun writeUnsignedSmart(out: ByteArrayOutputStream, value: Int) {
        require(value in 0..32767) { "Smart value must be between 0 and 32767: $value" }
        if (value < 128) {
            out.write(value)
        } else {
            writeUnsignedShort(out, value + 0x8000)
        }
    }

    private fun writeUnsignedShort(out: ByteArrayOutputStream, value: Int) {
        require(value in 0..0xFFFF) { "Unsigned short must be between 0 and 65535: $value" }
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun packedPosition(objectPlacement: OsrsObjectPlacement): Int =
        objectPlacement.plane shl 12 or
            (objectPlacement.x shl 6) or
            objectPlacement.y

    private fun normaliseExplicitHeight(value: Int): Int = if (value == 1) 0 else value

    private fun defaultBaseHeightAtWorldNoiseCoordinate(worldX: Int, worldY: Int): Int =
        defaultBaseHeight(worldX + GENERATED_HEIGHT_X_OFFSET, worldY + GENERATED_HEIGHT_Y_OFFSET)

    private fun defaultBaseHeight(worldX: Int, worldY: Int): Int {
        var height =
            interpolatedNoise(worldX + 45365, worldY + 0x16713, 4) - 128 +
                ((interpolatedNoise(worldX + 10294, worldY + 37821, 2) - 128) shr 1) +
                ((interpolatedNoise(worldX, worldY, 1) - 128) shr 2)
        height = (height * 0.3).toInt() + 35
        return max(10, min(60, height))
    }

    private fun interpolatedNoise(x: Int, y: Int, scale: Int): Int {
        val sampleX = x / scale
        val offsetX = x and (scale - 1)
        val sampleY = y / scale
        val offsetY = y and (scale - 1)
        val a = smoothNoise(sampleX, sampleY)
        val b = smoothNoise(sampleX + 1, sampleY)
        val c = smoothNoise(sampleX, sampleY + 1)
        val d = smoothNoise(sampleX + 1, sampleY + 1)
        return interpolate(
            interpolate(a, b, offsetX, scale),
            interpolate(c, d, offsetX, scale),
            offsetY,
            scale,
        )
    }

    private fun interpolate(a: Int, b: Int, offset: Int, scale: Int): Int {
        val cosine = (0x10000 - HEIGHT_COSINE[1024 * offset / scale]) shr 1
        return (a * (0x10000 - cosine) shr 16) + (b * cosine shr 16)
    }

    private fun smoothNoise(x: Int, y: Int): Int {
        val corners =
            noise(x - 1, y - 1) + noise(x + 1, y - 1) +
                noise(x - 1, y + 1) + noise(x + 1, y + 1)
        val sides =
            noise(x - 1, y) + noise(x + 1, y) +
                noise(x, y - 1) + noise(x, y + 1)
        return corners / 16 + sides / 8 + noise(x, y) / 4
    }

    private fun noise(x: Int, y: Int): Int {
        var value = x + y * 57
        value = (value shl 13) xor value
        value = (value * (value * value * 15731 + 0xC0AE5) + 0x5208DD0D) and 0x7FFFFFFF
        return (value shr 19) and 0xFF
    }

    private fun cube(): Array<Array<IntArray>> =
        Array(OsrsRegionData.PLANES) {
            Array(OsrsRegionData.REGION_SIZE) {
                IntArray(OsrsRegionData.REGION_SIZE)
            }
        }

    private class Cursor(
        private val data: ByteArray,
    ) {
        private var position = 0

        fun remaining(): Int = data.size - position

        fun peekUnsignedByte(): Int {
            requireBytes(1)
            return data[position].toInt() and 0xFF
        }

        fun readUnsignedByte(): Int {
            requireBytes(1)
            return data[position++].toInt() and 0xFF
        }

        fun readUnsignedShort(): Int {
            requireBytes(2)
            return (data[position++].toInt() and 0xFF shl 8) or
                (data[position++].toInt() and 0xFF)
        }

        private fun requireBytes(bytes: Int) {
            require(remaining() >= bytes) {
                "Truncated OSRS map payload at byte $position"
            }
        }
    }

    private val HEIGHT_COSINE =
        IntArray(2048) { index ->
            (65536.0 * cos(index * Math.PI / 1024.0)).toInt()
        }

    private const val GENERATED_HEIGHT_X_OFFSET = 932731
    private const val GENERATED_HEIGHT_Y_OFFSET = 556238
}
