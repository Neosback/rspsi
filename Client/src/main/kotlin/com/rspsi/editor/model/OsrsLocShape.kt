package com.rspsi.editor.model

import java.util.Optional

/**
 * Canonical OSRS location shapes used by map data and editor inspection.
 *
 * The category mapping follows OpenRune-Server's neutral routefinder constants. Roof and
 * centrepiece shapes are ground-layer locations; semantic shape ownership lives here rather
 * than in a renderer-specific object class.
 */
enum class OsrsLocShape(
    private val idValue: Int,
    private val categoryValue: ObjectCategory,
    private val displayNameValue: String,
) {
    WALL_STRAIGHT(0, ObjectCategory.WALL, "Straight wall"),
    WALL_DIAGONAL_CORNER(1, ObjectCategory.WALL, "Diagonal wall corner"),
    WALL_L(2, ObjectCategory.WALL, "L-shaped wall"),
    WALL_SQUARE_CORNER(3, ObjectCategory.WALL, "Square wall corner"),
    WALL_DECOR_STRAIGHT_NO_OFFSET(4, ObjectCategory.WALL_DECOR, "Straight wall decor"),
    WALL_DECOR_STRAIGHT_OFFSET(5, ObjectCategory.WALL_DECOR, "Offset wall decor"),
    WALL_DECOR_DIAGONAL_OFFSET(6, ObjectCategory.WALL_DECOR, "Diagonal offset wall decor"),
    WALL_DECOR_DIAGONAL_NO_OFFSET(7, ObjectCategory.WALL_DECOR, "Diagonal wall decor"),
    WALL_DECOR_DIAGONAL_BOTH(8, ObjectCategory.WALL_DECOR, "Two-sided diagonal wall decor"),
    WALL_DIAGONAL(9, ObjectCategory.GROUND, "Diagonal game object"),
    CENTREPIECE_STRAIGHT(10, ObjectCategory.GROUND, "Straight game object"),
    CENTREPIECE_DIAGONAL(11, ObjectCategory.GROUND, "Diagonal game object"),
    ROOF_STRAIGHT(12, ObjectCategory.GROUND, "Straight roof"),
    ROOF_DIAGONAL_WITH_ROOF_EDGE(13, ObjectCategory.GROUND, "Diagonal roof with edge"),
    ROOF_DIAGONAL(14, ObjectCategory.GROUND, "Diagonal roof"),
    ROOF_L_CONCAVE(15, ObjectCategory.GROUND, "Concave L roof"),
    ROOF_L_CONVEX(16, ObjectCategory.GROUND, "Convex L roof"),
    ROOF_FLAT(17, ObjectCategory.GROUND, "Flat roof"),
    ROOF_EDGE_STRAIGHT(18, ObjectCategory.GROUND, "Straight roof edge"),
    ROOF_EDGE_DIAGONAL_CORNER(19, ObjectCategory.GROUND, "Diagonal roof-edge corner"),
    ROOF_EDGE_L(20, ObjectCategory.GROUND, "L-shaped roof edge"),
    ROOF_EDGE_SQUARE_CORNER(21, ObjectCategory.GROUND, "Square roof-edge corner"),
    GROUND_DECOR(22, ObjectCategory.GROUND_DECOR, "Ground decor");

    fun id(): Int = idValue

    fun category(): ObjectCategory = categoryValue

    fun displayName(): String = displayNameValue

    companion object {
        private const val MAX_ID = 22

        /**
         * Shape IDs are the fixed contiguous OSRS range 0..22. Indexing this immutable table
         * avoids allocating/streaming across every enum constant for hot inspector lookups.
         */
        private val BY_ID: Array<OsrsLocShape?> =
            arrayOfNulls<OsrsLocShape>(MAX_ID + 1).also { table ->
                values().forEach { shape ->
                    table[shape.idValue] = shape
                }
            }

        @JvmStatic
        fun fromId(id: Int): Optional<OsrsLocShape> =
            if (id in BY_ID.indices) {
                Optional.ofNullable(BY_ID[id])
            } else {
                Optional.empty()
            }
    }
}
