package com.rspsi.editor.model

/**
 * A place the editor can go to: a whole map region or one world tile.
 *
 * Parsed from the text people already use for OSRS locations, so the dashboard's region
 * field, Map Studio's Go To dialog and the command palette all share one grammar.
 * [plane] is -1 when the text did not name one; callers keep the current plane.
 */
@JvmRecord
data class WorldLocation(
    val x: Int,
    val y: Int,
    val plane: Int,
    val kind: Kind,
) {
    enum class Kind { REGION, TILE }

    init {
        require(x in 0 until WORLD_SIZE && y in 0 until WORLD_SIZE) { "Outside the OSRS world: $x,$y" }
        require(plane in -1..3) { "Plane must be 0..3: $plane" }
    }

    fun regionX(): Int = x shr 6

    fun regionY(): Int = y shr 6

    fun regionId(): Int = (regionX() shl 8) or regionY()

    fun isTile(): Boolean = kind == Kind.TILE

    /** The tile to frame, on [fallbackPlane] when this location does not name a plane. */
    fun tile(fallbackPlane: Int): WorldTile = WorldTile(if (plane >= 0) plane else fallbackPlane, x, y)

    fun describe(): String {
        val region = "region ${regionX()},${regionY()} (id ${regionId()})"
        val planeText = if (plane >= 0) ", plane $plane" else ""
        return if (kind == Kind.TILE) "tile $x,$y$planeText in $region" else "$region$planeText"
    }

    companion object {
        private const val REGION_SIZE = 64
        private const val WORLD_SIZE = 256 * REGION_SIZE
        private val SEPARATORS = Regex("[,\\s_]+")

        /**
         * Parses a location, or returns null when the text is not one:
         * - `12850`: region id
         * - `50,50` or `50 50`: region x,y (both at most 255)
         * - `3222,3218` or `3222 3218 1`: world tile x,y and optional plane
         * - `0,50,50,22,18` or `0_50_50_22_18`: plane, region x,y, local x,y (the client's
         *   developer teleport format)
         */
        @JvmStatic
        fun parse(text: String?): WorldLocation? {
            if (text == null) return null
            val trimmed = text.trim().trim('(', ')', '[', ']')
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(SEPARATORS).map { it.toIntOrNull() ?: return null }
            return when (parts.size) {
                1 -> region(parts[0])
                2 -> pair(parts[0], parts[1], -1)
                3 -> pair(parts[0], parts[1], parts[2])
                5 -> jagex(parts)
                else -> null
            }
        }

        /** The centre of region [id], or null outside 0..65535. */
        @JvmStatic
        fun ofRegionId(id: Int): WorldLocation? = region(id)

        /** World tile [x],[y] on [plane] (0..3, or -1 to keep the current plane), or null. */
        @JvmStatic
        fun ofTile(x: Int, y: Int, plane: Int): WorldLocation? =
            if (plane in -1..3) tile(x, y, plane) else null

        private fun region(id: Int): WorldLocation? {
            if (id !in 0..0xFFFF) return null
            return regionCenter(id shr 8, id and 0xFF, -1)
        }

        private fun pair(a: Int, b: Int, plane: Int): WorldLocation? {
            if (plane !in -1..3) return null
            // No map region sits at world tiles 0..255 on both axes, so small pairs are regions.
            if (a in 0..255 && b in 0..255) return regionCenter(a, b, plane)
            return tile(a, b, plane)
        }

        private fun jagex(parts: List<Int>): WorldLocation? {
            val (plane, regionX, regionY, localX, localY) = parts
            if (plane !in 0..3 || regionX !in 0..255 || regionY !in 0..255 ||
                localX !in 0 until REGION_SIZE || localY !in 0 until REGION_SIZE
            ) {
                return null
            }
            return tile(regionX * REGION_SIZE + localX, regionY * REGION_SIZE + localY, plane)
        }

        private fun regionCenter(regionX: Int, regionY: Int, plane: Int): WorldLocation =
            WorldLocation(regionX * REGION_SIZE + REGION_SIZE / 2, regionY * REGION_SIZE + REGION_SIZE / 2,
                plane, Kind.REGION)

        private fun tile(x: Int, y: Int, plane: Int): WorldLocation? =
            if (x in 0 until WORLD_SIZE && y in 0 until WORLD_SIZE) WorldLocation(x, y, plane, Kind.TILE) else null
    }
}
