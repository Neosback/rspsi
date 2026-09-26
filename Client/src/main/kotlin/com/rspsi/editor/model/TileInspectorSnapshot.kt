package com.rspsi.editor.model

/**
 * Immutable, UI-neutral tile inspection payload.
 *
 * The inspector surface consumes this snapshot but does not own tile semantics. Address data
 * remains absolute-world metadata in [WorldTileAddress], while [TileSnapshot] remains the
 * authored tile state. This record only packages those facts for status bars, panels, overlays,
 * or other frontends.
 */
@JvmRecord
data class TileInspectorSnapshot(
    val address: WorldTileAddress,
    val tile: TileSnapshot,
    val bridge: Boolean,
    val roofRelated: Boolean,
) {
    /** Exposes the authored tile flag byte/int without making frontends reach into TileSnapshot. */
    fun rawFlags(): Int = tile.flags()

    override fun toString(): String =
        "TileInspectorSnapshot[address=$address, tile=$tile, bridge=$bridge, roofRelated=$roofRelated]"
}
