package com.rspsi.editor.model

/**
 * One terrain-height discontinuity across two adjacent loaded OSRS regions.
 *
 * [alongEdge] is the local coordinate along the 64-tile region edge. [upperCorner]
 * distinguishes the second vertex on that edge so diagnostics can identify the exact
 * shared height sample rather than only the neighboring tiles.
 */
@JvmRecord
data class RegionBoundaryMismatch(
    val direction: RegionBoundaryDirection,
    val plane: Int,
    val regionX: Int,
    val regionY: Int,
    val alongEdge: Int,
    val upperCorner: Boolean,
    val expected: Int,
    val actual: Int,
) {
    init {
        if (
            plane < 0 ||
            regionX < 0 ||
            regionY < 0 ||
            alongEdge < 0 ||
            alongEdge >= REGION_SIZE
        ) {
            throw IllegalArgumentException("Invalid region boundary location")
        }
    }

    override fun toString(): String =
        "RegionBoundaryMismatch[" +
            "direction=$direction, " +
            "plane=$plane, " +
            "regionX=$regionX, " +
            "regionY=$regionY, " +
            "alongEdge=$alongEdge, " +
            "upperCorner=$upperCorner, " +
            "expected=$expected, " +
            "actual=$actual]"

    companion object {
        private const val REGION_SIZE = 64
    }
}
