package com.rspsi.editor.model

/**
 * Explicit authored-plane to effective-plane relationship for one bridge tile.
 *
 * Bridge links are positional metadata, not renderer state: upper and lower must refer to
 * the same X/Y coordinate on adjacent planes. Kept as a JVM record for Java compatibility.
 */
@JvmRecord
data class BridgeLink(
    val upper: TileCoordinate,
    val lower: TileCoordinate,
) {
    init {
        if (
            upper.x != lower.x ||
            upper.y != lower.y ||
            upper.plane != lower.plane + 1
        ) {
            throw IllegalArgumentException(
                "Bridge links must connect adjacent planes at one tile",
            )
        }
    }

    override fun toString(): String =
        "BridgeLink[upper=$upper, lower=$lower]"
}
