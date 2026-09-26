package com.rspsi.editor.model

/**
 * Neutral OSRS coordinate breakdown used by inspectors and debug overlays.
 *
 * Region and chunk values are derived from absolute world coordinates, not cache APIs.
 * The canonical [of] factory computes the complete breakdown. The public record constructor
 * intentionally retains its historical validation contract rather than silently re-deriving
 * every supplied field during this migration.
 */
@JvmRecord
data class WorldTileAddress(
    val worldX: Int,
    val worldY: Int,
    val plane: Int,
    val regionId: Int,
    val regionX: Int,
    val regionY: Int,
    val regionLocalX: Int,
    val regionLocalY: Int,
    val chunkX: Int,
    val chunkY: Int,
    val chunkLocalX: Int,
    val chunkLocalY: Int,
) {
    init {
        if (worldX < 0 || worldY < 0 || plane < 0) {
            throw IllegalArgumentException("OSRS tile coordinates cannot be negative")
        }
        if (
            regionId != ((regionX shl 8) or regionY) ||
            regionLocalX != (worldX and REGION_MASK) ||
            regionLocalY != (worldY and REGION_MASK) ||
            chunkLocalX != (worldX and CHUNK_MASK) ||
            chunkLocalY != (worldY and CHUNK_MASK)
        ) {
            throw IllegalArgumentException("Inconsistent OSRS tile address")
        }
    }

    override fun toString(): String =
        "WorldTileAddress[" +
            "worldX=$worldX, " +
            "worldY=$worldY, " +
            "plane=$plane, " +
            "regionId=$regionId, " +
            "regionX=$regionX, " +
            "regionY=$regionY, " +
            "regionLocalX=$regionLocalX, " +
            "regionLocalY=$regionLocalY, " +
            "chunkX=$chunkX, " +
            "chunkY=$chunkY, " +
            "chunkLocalX=$chunkLocalX, " +
            "chunkLocalY=$chunkLocalY]"

    companion object {
        private const val REGION_SHIFT = 6
        private const val REGION_MASK = 63
        private const val CHUNK_SHIFT = 3
        private const val CHUNK_MASK = 7

        /** Derives the canonical region/chunk breakdown for one absolute OSRS tile. */
        @JvmStatic
        fun of(
            worldX: Int,
            worldY: Int,
            plane: Int,
        ): WorldTileAddress {
            if (worldX < 0 || worldY < 0 || plane < 0) {
                throw IllegalArgumentException("OSRS tile coordinates cannot be negative")
            }

            val regionX = worldX shr REGION_SHIFT
            val regionY = worldY shr REGION_SHIFT
            return WorldTileAddress(
                worldX,
                worldY,
                plane,
                (regionX shl 8) or regionY,
                regionX,
                regionY,
                worldX and REGION_MASK,
                worldY and REGION_MASK,
                worldX shr CHUNK_SHIFT,
                worldY shr CHUNK_SHIFT,
                worldX and CHUNK_MASK,
                worldY and CHUNK_MASK,
            )
        }
    }
}
