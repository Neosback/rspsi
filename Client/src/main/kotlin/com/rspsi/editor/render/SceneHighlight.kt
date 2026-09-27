package com.rspsi.editor.render

import com.rspsi.editor.model.WorldTileAddress

/**
 * Draw-command indices of one [GpuUploadPlan] to outline: what the pointer is over and what
 * is selected. Renderer-neutral; a backend draws these commands into a mask and outlines it.
 */
class SceneHighlight(
    hovered: IntArray,
    selected: IntArray,
) {
    val hovered: IntArray = hovered.copyOf()
    val selected: IntArray = selected.copyOf()

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
 * Finds the draw commands that belong to a tile or a placed object in one plan.
 *
 * Commands are grouped by their authored tile once per plan; every lookup then scans only the
 * handful of commands on one tile. A location's commands live on its anchor tile.
 */
class GpuHighlightIndex(val plan: GpuUploadPlan) {
    private val byTile = HashMap<Long, IntArray>()

    init {
        val grouped = HashMap<Long, MutableList<Int>>()
        plan.commands().forEachIndexed { index, command ->
            grouped.getOrPut(key(command.tile())) { ArrayList(4) }.add(index)
        }
        grouped.forEach { (tile, indices) -> byTile[tile] = indices.toIntArray() }
    }

    /** Terrain draws of one tile: its underlay/overlay shape as the client paints it. */
    fun terrain(tile: WorldTileAddress): IntArray =
        filter(tile) { it.layer() == SceneLayer.Kind.TERRAIN }

    /** Every model draw of one placed location, matched by id, type and rotation on its anchor. */
    fun location(anchor: WorldTileAddress, objectId: Int, type: Int, rotation: Int): IntArray =
        filter(anchor) {
            it.layer() != SceneLayer.Kind.TERRAIN &&
                it.objectId() == objectId &&
                (!it.sceneObjectIdentity().present() ||
                    (it.sceneObjectIdentity().shape() == type && it.sceneObjectIdentity().rotation() == rotation))
        }

    /** Every model draw sharing a picked command's scene identity. */
    fun location(anchor: WorldTileAddress, identity: SceneObjectIdentity): IntArray =
        filter(anchor) { it.layer() != SceneLayer.Kind.TERRAIN && it.sceneObjectIdentity() == identity }

    private inline fun filter(tile: WorldTileAddress, predicate: (GpuDrawCommand) -> Boolean): IntArray {
        val indices = byTile[key(tile)] ?: return EMPTY
        val commands = plan.commands()
        var count = 0
        val result = IntArray(indices.size)
        for (index in indices) {
            if (predicate(commands[index])) result[count++] = index
        }
        return if (count == result.size) result else result.copyOf(count)
    }

    private companion object {
        val EMPTY = IntArray(0)

        fun key(tile: WorldTileAddress): Long =
            (tile.plane.toLong() shl 40) or (tile.worldX.toLong() shl 20) or tile.worldY.toLong()
    }
}
