package com.rspsi.editor.terrain

import com.rspsi.editor.EditorCommand
import com.rspsi.editor.SetTerrainHeightCommand
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldDocument

/**
 * One terrain-height edit: vertex writes predicted on a copy of the world, then turned into
 * undoable per-tile commands.
 *
 * Terrain heights live on shared tile corners, so setting one vertex changes up to four
 * tiles. Every height tool (sculpting, flattening, spline ramps, the tile painter, core
 * services and quick context actions) goes through this one path: write vertices on
 * [predicted] through [TerrainVertexLattice], then emit a [SetTerrainHeightCommand] for each
 * tile that actually changed. The session's world is never touched until the commands run.
 *
 * Callers that keep editing the prediction after heights (e.g. materials) pass their own
 * [predicted] document so every step builds on the same copy.
 */
class TerrainHeightEdit @JvmOverloads constructor(
    private val original: WorldDocument,
    /** The copy edits are predicted on; defaults to a fresh copy of [original]. */
    val predicted: WorldDocument = original.copy(),
) {
    private val source = TerrainVertexLattice(original)
    private val target = TerrainVertexLattice(predicted)
    private val affected = LinkedHashSet<TileCoordinate>()

    /** The vertex's height before this edit. */
    fun originalHeight(plane: Int, vertexX: Int, vertexY: Int): Int = source.height(plane, vertexX, vertexY)

    /** Sets one shared corner vertex. */
    fun setVertex(plane: Int, vertexX: Int, vertexY: Int, height: Int) {
        affected += target.setHeight(plane, vertexX, vertexY, height)
    }

    /** Moves one vertex by [delta] from its original height. */
    fun raiseVertex(plane: Int, vertexX: Int, vertexY: Int, delta: Int) =
        setVertex(plane, vertexX, vertexY, originalHeight(plane, vertexX, vertexY) + delta)

    /** Sets all four corners of [tile] to [height], flattening it. */
    fun setTile(tile: TileCoordinate, height: Int) = forEachCorner(tile) { x, y -> setVertex(tile.plane, x, y, height) }

    /** Moves all four corners of [tile] by [delta] from their original heights. */
    fun raiseTile(tile: TileCoordinate, delta: Int) = forEachCorner(tile) { x, y -> raiseVertex(tile.plane, x, y, delta) }

    /** One command per tile whose snapshot changed, in the order tiles were first affected. */
    fun commands(description: (TileCoordinate) -> String): List<EditorCommand> {
        val commands = ArrayList<EditorCommand>(affected.size)
        for (tile in affected) {
            val before = original.tile(tile).snapshot()
            val after = predicted.tile(tile).snapshot()
            if (before != after) {
                commands += SetTerrainHeightCommand(tile, before, after, before.heightSource(), after.heightSource(),
                    description(tile))
            }
        }
        return commands
    }

    /** Java-friendly [commands] with one description for every tile. */
    fun commands(description: String): List<EditorCommand> = commands { description }

    private inline fun forEachCorner(tile: TileCoordinate, action: (Int, Int) -> Unit) {
        action(tile.x, tile.y)
        action(tile.x + 1, tile.y)
        action(tile.x + 1, tile.y + 1)
        action(tile.x, tile.y + 1)
    }
}
