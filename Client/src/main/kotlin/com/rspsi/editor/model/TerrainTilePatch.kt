package com.rspsi.editor.model

/**
 * A terrain snapshot at its source coordinate inside a world fragment.
 *
 * Coordinates are fragment-local and non-negative. This remains a JVM record so existing
 * Java serialization/introspection and record-style accessors continue to work unchanged.
 */
@JvmRecord
data class TerrainTilePatch(
    val plane: Int,
    val x: Int,
    val y: Int,
    val snapshot: TileSnapshot,
) {
    init {
        if (plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("Fragment tile coordinates cannot be negative")
        }
    }

    override fun toString(): String =
        "TerrainTilePatch[plane=$plane, x=$x, y=$y, snapshot=$snapshot]"
}
