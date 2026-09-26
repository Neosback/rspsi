package com.rspsi.editor.model

import java.util.Optional

/**
 * Backend-neutral object placement used by editor operations.
 *
 * Object shape/category semantics stay in [OsrsLocShape] and [ObjectCategory]; this record
 * only owns placement identity, rotation, and the small RuneLite-compatible wall orientation
 * derivations needed by inspectors/overlays.
 */
@JvmRecord
data class WorldObject(
    val id: Int,
    val type: Int,
    val rotation: Int,
    val plane: Int,
    val x: Int,
    val y: Int,
) {
    init {
        if (id < 0 || type < 0 || plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("World object values cannot be negative")
        }
        if (rotation !in 0..3) {
            throw IllegalArgumentException("Object rotation must be between 0 and 3")
        }
    }

    /** Returns the neutral scene category implied by this object's OSRS shape. */
    fun category(): ObjectCategory = ObjectCategory.fromType(type)

    /** Returns the semantic shape when the OSRS type is supported. */
    fun shape(): Optional<OsrsLocShape> = OsrsLocShape.fromId(type)

    /**
     * RuneLite WallObject.getOrientationA() semantics for this location.
     *
     * Cardinal wall shapes use 1/2/4/8 for west/north/east/south. Diagonal wall shapes
     * use 16/32/64/128 for north-west/north-east/south-east/south-west.
     */
    fun wallOrientationA(): Int =
        when (type) {
            0, 2 -> 1 shl rotation
            1, 3 -> 1 shl (rotation + 4)
            else -> 0
        }

    /** RuneLite WallObject.getOrientationB() for the second segment of an L-wall. */
    fun wallOrientationB(): Int =
        if (type == 2) {
            1 shl ((rotation + 1) and 3)
        } else {
            0
        }

    override fun toString(): String =
        "WorldObject[id=$id, type=$type, rotation=$rotation, plane=$plane, x=$x, y=$y]"
}
