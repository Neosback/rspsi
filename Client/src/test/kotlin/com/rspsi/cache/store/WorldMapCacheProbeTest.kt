package com.rspsi.cache.store

import dev.openrune.cache.worldmap.worldmap.WorldMapAreaDetails
import dev.openrune.cache.worldmap.worldmap.MapsquareMultiSection
import dev.openrune.cache.worldmap.worldmap.MapsquareSingleSection
import dev.openrune.cache.worldmap.worldmap.ZoneMultiSection
import dev.openrune.cache.worldmap.worldmap.ZoneSingleSection
import dev.openrune.filesystem.Cache
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

class WorldMapCacheProbeTest {

    @Test
    fun probeWorldMapArchive() {
        val cacheDir = File("/Users/tylercovalt/Documents/ChatGPT/RSPSi-master/OpenRune-Server-main/.data/cache/LIVE")
        if (!cacheDir.exists()) return
        try {
            val cache = Cache.load(cacheDir.toPath())
            val detailsArchives = cache.files(19, 0)
            println("Found ${detailsArchives.size} area details in Archive 19:")
            for (fileId in detailsArchives) {
                val data = cache.data(19, 0, fileId, null) ?: continue
                val area = WorldMapAreaDetails.decode(fileId, Unpooled.wrappedBuffer(data))
                val hasComp = cache.data(19, 2, fileId, null) != null
                println("  Area $fileId: internalName='${area.internalName}', displayName='${area.displayName}', isMain=${area.isMain}, origin=${area.origin}, hasCompositeTexture=$hasComp")
                if (fileId == 0) {
                    println("  Area 0 sections count: ${area.sections.size}")
                    for (sec in area.sections) {
                        println("    Section class: ${sec.javaClass.name}, sec=$sec")
                    }
                }
            }
            
            val compBytes = cache.data(19, 2, 0, null)
            if (compBytes != null) {
                val img = ImageIO.read(ByteArrayInputStream(compBytes))
                println("Area 0 CompositeTexture image: ${img.width} x ${img.height}")
            }
            // Probe compositemap (archive 19, group 1, file 0)
            val compMapData = cache.data(19, 1, 0, null)
            if (compMapData != null) {
                val buf = Unpooled.wrappedBuffer(compMapData)
                val squareCount = buf.readUnsignedShort()
                println("Area 0 compositemap: squareCount=$squareCount")
                // skip squares
                for (i in 0 until squareCount) {
                    val marker = buf.readUnsignedByte()
                    val level = buf.readUnsignedByte()
                    val levelsCount = buf.readUnsignedByte()
                    val srcX = buf.readUnsignedShort()
                    val srcY = buf.readUnsignedShort()
                    val dstX = buf.readUnsignedShort()
                    val dstY = buf.readUnsignedShort()
                }
                val zoneCount = buf.readUnsignedShort()
                println("Area 0 compositemap: zoneCount=$zoneCount")
                for (i in 0 until zoneCount) {
                    val marker = buf.readUnsignedByte()
                    val level = buf.readUnsignedByte()
                    val levelsCount = buf.readUnsignedByte()
                    val srcX = buf.readUnsignedShort()
                    val srcY = buf.readUnsignedShort()
                    val srcZx = buf.readUnsignedByte()
                    val srcZy = buf.readUnsignedByte()
                    val dstX = buf.readUnsignedShort()
                    val dstY = buf.readUnsignedShort()
                    val dstZx = buf.readUnsignedByte()
                    val dstZy = buf.readUnsignedByte()
                }
                val elementCount = buf.readUnsignedShort()
                println("Area 0 compositemap: elementCount=$elementCount")
                for (i in 0 until minOf(elementCount, 5)) {
                    val elemId = buf.readInt()
                    val coord = buf.readInt()
                    val members = buf.readUnsignedByte()
                    val plane = (coord ushr 28) and 3
                    val x = (coord ushr 14) and 0x3fff
                    val y = coord and 0x3fff
                    println("  element $i: elemId=$elemId coord=($x, $y, $plane)")
                }
            }
            // Check Index 20 (WORLDMAP_GROUND)
            val archives20 = cache.archives(20)
            println("Index 20 archive count: ${archives20.size}, sample archives: ${archives20.take(10)}")
            // Lumbridge mapsquare id = (50 << 8) | 50 = 12850
            val lumbridgeSquare = (50 shl 8) or 50
            // Verify OpenRuneWorldMapLoader using OpenRuneCacheStore
            val store = OpenRuneCacheStore(cache)
            org.junit.jupiter.api.Assertions.assertTrue(com.rspsi.cache.store.OpenRuneWorldMapLoader.hasWorldMap(store))
            val areas = com.rspsi.cache.store.OpenRuneWorldMapLoader.loadAreas(store)
            org.junit.jupiter.api.Assertions.assertTrue(areas.isNotEmpty())
            val mainArea = areas.first()
            org.junit.jupiter.api.Assertions.assertEquals("Gielinor Surface", mainArea.displayName)
            org.junit.jupiter.api.Assertions.assertTrue(mainArea.isMain)
            org.junit.jupiter.api.Assertions.assertEquals(15, mainArea.regionLowX)
            org.junit.jupiter.api.Assertions.assertEquals(62, mainArea.regionHighX)
            org.junit.jupiter.api.Assertions.assertEquals(32, mainArea.regionLowY)
            org.junit.jupiter.api.Assertions.assertEquals(65, mainArea.regionHighY)

            val compImg = com.rspsi.cache.store.OpenRuneWorldMapLoader.loadCompositeTexture(store, mainArea.id)
            org.junit.jupiter.api.Assertions.assertNotNull(compImg)
            org.junit.jupiter.api.Assertions.assertEquals(768, compImg!!.width)
            org.junit.jupiter.api.Assertions.assertEquals(544, compImg.height)

            val groundImg = com.rspsi.cache.store.OpenRuneWorldMapLoader.loadRegionGround(store, 50, 50)
            org.junit.jupiter.api.Assertions.assertNotNull(groundImg)
            org.junit.jupiter.api.Assertions.assertEquals(64, groundImg!!.width)
            org.junit.jupiter.api.Assertions.assertEquals(64, groundImg.height)
            println("OpenRuneWorldMapLoader verification passed: loaded ${areas.size} areas, main composite ${compImg.width}x${compImg.height}, ground tile ${groundImg.width}x${groundImg.height}")
        } catch (e: Exception) {
            e.printStackTrace()
            org.junit.jupiter.api.Assertions.fail(e)
        }
    }
}

