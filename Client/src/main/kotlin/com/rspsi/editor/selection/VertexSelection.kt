package com.rspsi.editor.selection

/**
 * Terrain corner/vertex selection.
 *
 * Corner ordering is 0=SW, 1=SE, 2=NE, 3=NW.
 */
@JvmRecord
data class VertexSelection(
    val plane: Int,
    val x: Int,
    val y: Int,
    val corner: Int,
) : Selection {
    init {
        if (plane < 0 || x < 0 || y < 0 || corner !in 0..3) {
            throw IllegalArgumentException("Invalid terrain vertex selection")
        }
    }

    override fun toString(): String =
        "VertexSelection[plane=$plane, x=$x, y=$y, corner=$corner]"
}
