package com.openrune.studio.core.osrs.map

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModernOsrsRegionCodecTest {
    @Test
    fun decodesModernTerrainAndMasksOverlayMarker() {
        val region =
            ModernOsrsRegionCodec.decodeTerrain(
                modernTerrainFixture(),
                10,
                20,
                ModernOsrsRegionCodec.BaseHeightProvider { _, _ -> 10 },
            )

        val ground = region.tile(0, 0, 0)
        val upper = region.tile(1, 0, 0)

        assertEquals(-40, ground.southWestHeight)
        assertEquals(4, ground.overlayId)
        assertEquals(6, ground.overlayShape)
        assertEquals(3, ground.overlayRotation)
        assertEquals(6, ground.flags)
        assertEquals(7, ground.underlayId)
        assertEquals(1, region.tile(0, 2, 0).overlayId)
        assertEquals(-56, upper.southWestHeight)
        assertTrue(ground.heightSource.explicitValue == 5)
    }

    @Test
    fun decodesModernDeltaPackedLocations() {
        val objectPlacement = ModernOsrsRegionCodec.decodeLocations(locationFixture()).single()

        assertEquals(
            OsrsObjectPlacement(
                id = 100,
                type = 10,
                rotation = 2,
                plane = 1,
                x = 3,
                y = 4,
            ),
            objectPlacement,
        )
    }

    @Test
    fun modernDecodeEncodeDecodePreservesSemanticRegionState() {
        val source = regionFixture()

        val terrain = ModernOsrsRegionCodec.encodeTerrain(source)
        val locations = ModernOsrsRegionCodec.encodeLocations(source)
        val decoded =
            ModernOsrsRegionCodec.decode(
                terrain,
                locations,
                source.regionX,
                source.regionY,
                ModernOsrsRegionCodec.BaseHeightProvider { _, _ -> 10 },
            )

        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    assertEquals(
                        source.tile(plane, x, y).copy(heightSource = decoded.tile(plane, x, y).heightSource),
                        decoded.tile(plane, x, y),
                        "semantic mismatch at $plane,$x,$y",
                    )
                }
            }
        }
    }

    @Test
    fun rejectsCrackedSharedTerrainEdges() {
        val tiles = MutableList(OsrsRegionData.TILE_COUNT) { OsrsTileData() }
        tiles[OsrsRegionData.index(0, 0, 0)] =
            OsrsTileData(southEastHeight = 8)

        val region = OsrsRegionData(0, 0, tiles)

        assertFailsWith<IllegalArgumentException> {
            ModernOsrsRegionCodec.encodeTerrain(region)
        }
    }

    @Test
    fun refusesPoisonHeightByteOne() {
        val tiles = MutableList(OsrsRegionData.TILE_COUNT) { OsrsTileData() }
        for (x in 0 until OsrsRegionData.REGION_SIZE) {
            for (y in 0 until OsrsRegionData.REGION_SIZE) {
                tiles[OsrsRegionData.index(1, x, y)] =
                    OsrsTileData(
                        southWestHeight = -8,
                        southEastHeight = -8,
                        northEastHeight = -8,
                        northWestHeight = -8,
                    )
            }
        }

        val error =
            assertFailsWith<IllegalArgumentException> {
                ModernOsrsRegionCodec.encodeTerrain(OsrsRegionData(0, 0, tiles))
            }
        assertTrue(error.message.orEmpty().contains("height byte 1"))
    }

    private fun regionFixture(): OsrsRegionData {
        val tiles = MutableList(OsrsRegionData.TILE_COUNT) { OsrsTileData() }

        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    val southWest = -16 * (x + y + plane * 15)
                    val eastX = if (x == 63) x else x + 1
                    val northY = if (y == 63) y else y + 1
                    val objects =
                        if (plane == 3 && x == 9 && y == 10) {
                            listOf(
                                OsrsObjectPlacement(
                                    id = 100,
                                    type = 10,
                                    rotation = 2,
                                    plane = 3,
                                    x = 9,
                                    y = 10,
                                ),
                            )
                        } else {
                            emptyList()
                        }

                    tiles[OsrsRegionData.index(plane, x, y)] =
                        OsrsTileData(
                            southWestHeight = southWest,
                            southEastHeight = -16 * (eastX + y + plane * 15),
                            northEastHeight = -16 * (eastX + northY + plane * 15),
                            northWestHeight = -16 * (x + northY + plane * 15),
                            underlayId = if (plane == 1 && x == 2 && y == 3) 7 else 0,
                            overlayId = if (plane == 0 && x == 4 && y == 5) 23 else 0,
                            overlayShape = if (plane == 0 && x == 4 && y == 5) 11 else 0,
                            overlayRotation = if (plane == 0 && x == 4 && y == 5) 1 else 0,
                            flags = if (plane == 2 && x == 7 && y == 8) 6 else 0,
                            objects = objects,
                        )
                }
            }
        }

        return OsrsRegionData(0, 0, tiles)
    }

    private fun modernTerrainFixture(): ByteArray {
        val out = ByteArrayOutputStream()
        for (plane in 0 until OsrsRegionData.PLANES) {
            for (x in 0 until OsrsRegionData.REGION_SIZE) {
                for (y in 0 until OsrsRegionData.REGION_SIZE) {
                    when {
                        plane == 0 && x == 0 && y == 0 -> {
                            writeUnsignedShort(out, 29)
                            writeUnsignedShort(out, 4)
                            writeUnsignedShort(out, 88)
                            writeUnsignedShort(out, 55)
                            writeUnsignedShort(out, 1)
                            out.write(5)
                        }

                        plane == 0 && x == 1 && y == 0 -> {
                            writeUnsignedShort(out, 2)
                            writeUnsignedShort(out, 1)
                            writeUnsignedShort(out, 0)
                        }

                        plane == 0 && x == 2 && y == 0 -> {
                            writeUnsignedShort(out, 2)
                            writeUnsignedShort(out, 0x8001)
                            writeUnsignedShort(out, 0)
                        }

                        plane == 1 && x == 0 && y == 0 -> {
                            writeUnsignedShort(out, 1)
                            out.write(2)
                        }

                        else -> writeUnsignedShort(out, 0)
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun locationFixture(): ByteArray {
        val out = ByteArrayOutputStream()
        writeUnsignedSmart(out, 101)
        val packedPosition = (1 shl 12) or (3 shl 6) or 4
        writeUnsignedSmart(out, packedPosition + 1)
        out.write((10 shl 2) or 2)
        out.write(0)
        out.write(0)
        return out.toByteArray()
    }

    private fun writeUnsignedSmart(out: ByteArrayOutputStream, value: Int) {
        if (value < 128) {
            out.write(value)
        } else {
            writeUnsignedShort(out, value + 0x8000)
        }
    }

    private fun writeUnsignedShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }
}
