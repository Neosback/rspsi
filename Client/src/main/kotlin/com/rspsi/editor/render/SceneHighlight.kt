package com.rspsi.editor.render

import com.rspsi.editor.model.WorldTileAddress

/**
 * Index ranges of one [GpuUploadPlan] to outline: what the pointer is over and what is
 * selected. Each range is three ints, `command, indexOffset, indexCount`, so a tile inside a
 * zone-merged terrain draw outlines only its own triangles. Renderer-neutral.
 */
class SceneHighlight(
    hovered: IntArray,
    selected: IntArray,
) {
    val hovered: IntArray = hovered.copyOf()
    val selected: IntArray = selected.copyOf()

    init {
        require(hovered.size % 3 == 0 && selected.size % 3 == 0) { "Highlight ranges are triples" }
    }

    fun isEmpty(): Boolean = hovered.isEmpty() && selected.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is SceneHighlight &&
            hovered.contentEquals(other.hovered) &&
            selected.contentEquals(other.selected)

    override fun hashCode(): Int = 31 * hovered.contentHashCode() + selected.contentHashCode()

    companion object {
        @JvmField
        val NONE = SceneHighlight(IntArray(0), IntArray(0))
    }
}

/**
 * Finds the draw ranges that belong to a tile or a placed object in one plan.
 *
 * Model draws never span tiles, so a location is every matching command on its anchor tile.
 * Terrain draws merge across an 8x8 zone, so a tile is found by scanning the zone's terrain
 * commands for the triangles whose vertices carry that tile. Results are cached per tile.
 */
class GpuHighlightIndex(val plan: GpuUploadPlan) {
    private val modelsByTile = HashMap<Long, IntArray>()
    private val terrainByZone = HashMap<Long, IntArray>()
    private val terrainCache = HashMap<Long, IntArray>()

    init {
        val models = HashMap<Long, MutableList<Int>>()
        val terrain = HashMap<Long, MutableList<Int>>()
        plan.commands().forEachIndexed { index, command ->
            val tile = command.tile()
            if (command.layer() == SceneLayer.Kind.TERRAIN) {
                terrain.getOrPut(zoneKey(tile.plane, tile.worldX, tile.worldY)) { ArrayList(8) }.add(index)
            } else {
                models.getOrPut(tileKey(tile.plane, tile.worldX, tile.worldY)) { ArrayList(4) }.add(index)
            }
        }
        models.forEach { (key, indices) -> modelsByTile[key] = indices.toIntArray() }
        terrain.forEach { (key, indices) -> terrainByZone[key] = indices.toIntArray() }
    }

    /** Terrain triangles of one tile: its underlay/overlay shape as the client paints it. */
    fun terrain(tile: WorldTileAddress): IntArray {
        val key = tileKey(tile.plane, tile.worldX, tile.worldY)
        return terrainCache.getOrPut(key) { scanTerrain(tile) }
    }

    /** Every model draw of one placed location, matched by id, type and rotation on its anchor. */
    fun location(anchor: WorldTileAddress, objectId: Int, type: Int, rotation: Int): IntArray =
        wholeCommands(anchor) {
            it.objectId() == objectId &&
                (!it.sceneObjectIdentity().present() ||
                    (it.sceneObjectIdentity().shape() == type && it.sceneObjectIdentity().rotation() == rotation))
        }

    /** Every model draw sharing a picked command's scene identity. */
    fun location(anchor: WorldTileAddress, identity: SceneObjectIdentity): IntArray =
        wholeCommands(anchor) { it.sceneObjectIdentity() == identity }

    /** Every model draw of one placed object id on its anchor. */
    fun location(anchor: WorldTileAddress, objectId: Int): IntArray =
        wholeCommands(anchor) { it.objectId() == objectId }

    /**
     * World-space triangle positions (x, y, z per vertex) of highlight [ranges]. A renderer
     * draws these directly, so an outline never depends on how the current frame's plan
     * happens to be zoned or numbered.
     */
    fun positions(ranges: IntArray): FloatArray {
        var total = 0
        var i = 0
        while (i + 2 < ranges.size) {
            total += ranges[i + 2]
            i += 3
        }
        val out = FloatArray(total * 3)
        var cursor = 0
        i = 0
        while (i + 2 < ranges.size) {
            val command = ranges[i]
            val offset = ranges[i + 1]
            val count = ranges[i + 2]
            for (k in 0 until count) {
                val vertex = plan.indexedVertex(command, offset + k)
                out[cursor++] = vertex.x()
                out[cursor++] = vertex.y()
                out[cursor++] = vertex.z()
            }
            i += 3
        }
        return out
    }

    private inline fun wholeCommands(tile: WorldTileAddress, predicate: (GpuDrawCommand) -> Boolean): IntArray {
        val indices = modelsByTile[tileKey(tile.plane, tile.worldX, tile.worldY)] ?: return EMPTY
        val commands = plan.commands()
        val ranges = IntRanges()
        for (index in indices) {
            val command = commands[index]
            if (predicate(command)) ranges.add(index, 0, command.indexCount())
        }
        return ranges.toIntArray()
    }

    private fun scanTerrain(tile: WorldTileAddress): IntArray {
        val candidates = terrainByZone[zoneKey(tile.plane, tile.worldX, tile.worldY)] ?: return EMPTY
        val commands = plan.commands()
        val ranges = IntRanges()
        for (index in candidates) {
            val count = commands[index].indexCount()
            var runStart = -1
            var offset = 0
            while (offset + 2 < count) {
                val vertex = plan.indexedVertex(index, offset)
                val inside = vertex.pickerPlane() == tile.plane &&
                    vertex.pickerTileX() == tile.worldX && vertex.pickerTileY() == tile.worldY
                if (inside && runStart < 0) runStart = offset
                if (!inside && runStart >= 0) {
                    ranges.add(index, runStart, offset - runStart)
                    runStart = -1
                }
                offset += 3
            }
            if (runStart >= 0) ranges.add(index, runStart, offset - runStart)
        }
        return ranges.toIntArray()
    }

    private class IntRanges {
        private var values = IntArray(12)
        private var size = 0

        fun add(command: Int, offset: Int, count: Int) {
            if (count <= 0) return
            if (size + 3 > values.size) values = values.copyOf(values.size * 2)
            values[size++] = command
            values[size++] = offset
            values[size++] = count
        }

        fun toIntArray(): IntArray = values.copyOf(size)
    }

    private companion object {
        val EMPTY = IntArray(0)

        fun tileKey(plane: Int, x: Int, y: Int): Long =
            (plane.toLong() shl 40) or (x.toLong() shl 20) or y.toLong()

        fun zoneKey(plane: Int, x: Int, y: Int): Long = tileKey(plane, x shr 3, y shr 3)
    }
}
