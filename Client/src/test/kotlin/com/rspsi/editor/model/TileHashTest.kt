package com.rspsi.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class TileHashTest {
    @Test
    fun aThreeByThreeRegionWindowHasNoHashCollisions() {
        // 3x3 regions plus a 5-tile border, every plane: the key set of a region load.
        val hashes = HashSet<Int>()
        var count = 0
        for (plane in 0 until 4) {
            for (x in 0 until 202) {
                for (y in 0 until 202) {
                    hashes += TileCoordinate(plane, x, y).hashCode()
                    count++
                }
            }
        }
        assertEquals(count, hashes.size)
    }

    @Test
    fun worldAddressesCompareByTheirWorldTile() {
        val a = WorldTileAddress.of(3222, 3218, 0)
        val b = WorldTileAddress.of(3222, 3218, 0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, WorldTileAddress.of(3222, 3218, 1))
        assertEquals(WorldTile(0, 3222, 3218).hashCode(), a.hashCode())
    }
}
