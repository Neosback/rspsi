package com.rspsi.editor.model

/**
 * Absolute OSRS world tile coordinate.
 *
 * Viewport picking, navigation, scene overlays, and cross-region services use this type.
 * Convert through [WorldWindow] before touching a [WorldDocument]; do not reinterpret a
 * WorldTile as a LocalTile merely because the numeric components happen to fit.
 */
@JvmRecord
data class WorldTile(
    val plane: Int,
    val x: Int,
    val y: Int,
) {
    init {
        if (plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("World tile coordinates cannot be negative")
        }
    }

    fun address(): WorldTileAddress = WorldTileAddress.of(x, y, plane)

    override fun toString(): String =
        "WorldTile[plane=$plane, x=$x, y=$y]"
}
