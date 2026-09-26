package com.rspsi.editor.model

/**
 * Canonical OSRS region identity paired with its editable 64x64 world document.
 *
 * Region coordinates are the high six-bit world-tile groups used by the cache/world model.
 * This remains a JVM record so Java callers retain record component accessors and reflection
 * while Kotlin gains data-class ergonomics.
 */
@JvmRecord
data class WorldRegion(
    val regionX: Int,
    val regionY: Int,
    val document: WorldDocument,
) {
    init {
        if (regionX !in 0..255 || regionY !in 0..255) {
            throw IllegalArgumentException(
                "OSRS region coordinates must be in [0, 255]",
            )
        }
        if (document.width() != REGION_SIZE || document.length() != REGION_SIZE) {
            throw IllegalArgumentException("An OSRS region document must be 64x64")
        }
    }

    /** Packs the canonical cache region id as X in the high byte and Y in the low byte. */
    fun regionId(): Int = (regionX shl 8) or regionY

    /**
     * Returns the absolute world-space window owned by this region.
     *
     * The editable document remains local 0..63; conversion to world coordinates occurs
     * through this window rather than by reinterpreting LocalTile values as WorldTile values.
     */
    fun window(): WorldWindow =
        WorldWindow(
            regionX * REGION_SIZE,
            regionY * REGION_SIZE,
            REGION_SIZE,
            REGION_SIZE,
        )

    override fun toString(): String =
        "WorldRegion[regionX=$regionX, regionY=$regionY, document=$document]"

    companion object {
        const val REGION_SIZE = 64
    }
}
