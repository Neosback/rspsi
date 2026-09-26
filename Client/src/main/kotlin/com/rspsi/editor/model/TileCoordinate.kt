package com.rspsi.editor.model

/**
 * Legacy document-local tile coordinate retained only for compatibility.
 *
 * New document-facing code should use [LocalTile]. Absolute OSRS coordinates must use
 * [WorldTile]. This type remains while command/history/selection contracts finish migrating,
 * so its Java record ABI, deprecation metadata, and validation behavior are intentionally preserved.
 */
@kotlin.Deprecated("Use LocalTile for document coordinates or WorldTile for absolute coordinates")
@java.lang.Deprecated
@JvmRecord
data class TileCoordinate(
    val plane: Int,
    val x: Int,
    val y: Int,
) {
    init {
        if (plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("Tile coordinates cannot be negative")
        }
    }

    override fun toString(): String =
        "TileCoordinate[plane=$plane, x=$x, y=$y]"
}
