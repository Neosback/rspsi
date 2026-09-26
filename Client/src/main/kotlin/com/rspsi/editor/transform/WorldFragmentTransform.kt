package com.rspsi.editor.transform

import java.util.Optional

/**
 * Orthogonal world-fragment transform.
 *
 * Mirrors are applied first, followed by quarter-turn rotation. The nullable Kotlin type on
 * [pivot] is an implementation detail used only to preserve the Java record constructor's exact
 * `NullPointerException("pivot")` behavior; valid instances never retain a null pivot.
 */
@JvmRecord
data class WorldFragmentTransform(
    val quarterTurns: Int,
    val mirrorX: Boolean,
    val mirrorY: Boolean,
    val pivot: Optional<Pivot>?,
) {
    init {
        if (quarterTurns !in 0..3) {
            throw IllegalArgumentException("Quarter turns must be in [0, 3]")
        }
        if (pivot == null) {
            throw NullPointerException("pivot")
        }
    }

    constructor(
        quarterTurns: Int,
        mirrorX: Boolean,
        mirrorY: Boolean,
    ) : this(quarterTurns, mirrorX, mirrorY, Optional.empty())

    /** Returns the same transform anchored around [pivot]. */
    fun around(pivot: Pivot?): WorldFragmentTransform {
        val safePivot = pivot ?: throw NullPointerException("pivot")
        return WorldFragmentTransform(
            quarterTurns,
            mirrorX,
            mirrorY,
            Optional.of(safePivot),
        )
    }

    override fun toString(): String =
        "WorldFragmentTransform[" +
            "quarterTurns=$quarterTurns, " +
            "mirrorX=$mirrorX, " +
            "mirrorY=$mirrorY, " +
            "pivot=$pivot]"

    /** Absolute source tile that must remain at the same world coordinate. */
    @JvmRecord
    data class Pivot(
        val x: Int,
        val y: Int,
    ) {
        init {
            if (x < 0 || y < 0) {
                throw IllegalArgumentException("Fragment pivot cannot be negative")
            }
        }

        override fun toString(): String =
            "Pivot[x=$x, y=$y]"
    }

    companion object {
        @JvmStatic
        fun identity(): WorldFragmentTransform =
            WorldFragmentTransform(0, false, false)

        @JvmStatic
        fun rotate(quarterTurns: Int): WorldFragmentTransform =
            WorldFragmentTransform(quarterTurns, false, false)

        @JvmStatic
        fun reflectX(): WorldFragmentTransform =
            WorldFragmentTransform(0, true, false)

        @JvmStatic
        fun reflectY(): WorldFragmentTransform =
            WorldFragmentTransform(0, false, true)
    }
}
