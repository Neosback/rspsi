package com.rspsi.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldRegionWindowAreaTest {
    private fun region(regionX: Int, regionY: Int, underlay: Int): WorldRegion {
        val document = WorldDocument(64, 64, 1)
        for (x in 0 until 64) for (y in 0 until 64) {
            document.tile(0, x, y).restore(TileSnapshot(0, 0, 0, 0, underlay, 0, 0, 0, 0, emptyList()))
        }
        return WorldRegion(regionX, regionY, document)
    }

    @Test
    fun anAreaAcrossARegionEdgeCopiesBothSidesAndShiftsObjects() {
        val west = region(50, 50, underlay = 1)
        val east = region(51, 50, underlay = 2)
        // An object on the east region's first column, local (0, 10).
        east.document.tile(0, 0, 10).restore(
            TileSnapshot(0, 0, 0, 0, 2, 0, 0, 0, 0, listOf(WorldObject(1234, 10, 0, 0, 0, 10))))
        val window = WorldRegionWindow(50, 50, 2, 1,
            mapOf(west.regionId() to west, east.regionId() to east))

        // Six tiles either side of the shared edge at world x = 51 * 64.
        val area = window.materializeArea(51 * 64 - 6, 50 * 64, 12, 20)

        assertEquals(12, area.width())
        assertEquals(1, area.tile(0, 5, 0).snapshot().underlayId())
        assertEquals(2, area.tile(0, 6, 0).snapshot().underlayId())
        val shifted = area.tile(0, 6, 10).snapshot().objects().single()
        assertEquals(6, shifted.x)
        assertEquals(10, shifted.y)
    }

    @Test
    fun theWholePaddedWindowIsTheSpecialCaseOfAnArea() {
        val only = region(50, 50, underlay = 3)
        val window = WorldRegionWindow(50, 50, 1, 1, mapOf(only.regionId() to only))

        val padded = window.materializePaddedWorldDocument(2)

        assertEquals(68, padded.width())
        assertEquals(0, padded.tile(0, 1, 1).snapshot().underlayId())
        assertEquals(3, padded.tile(0, 2, 2).snapshot().underlayId())
        assertEquals(3, padded.tile(0, 65, 65).snapshot().underlayId())
        assertEquals(0, padded.tile(0, 66, 66).snapshot().underlayId())
    }
}
