package com.rspsi.editor.model

/**
 * Neutral semantic scene layer for an OSRS location shape.
 *
 * The editor reasons about these categories independently of renderer-specific object
 * classes so inspectors, tools, and serialization can share one canonical mapping.
 */
enum class ObjectCategory(
    private val layerIdValue: Int,
    private val displayNameValue: String,
) {
    WALL(0, "Wall"),
    WALL_DECOR(1, "Wall decor"),
    GROUND(2, "Game object"),
    GROUND_DECOR(3, "Ground decor"),
    UNKNOWN(-1, "Unknown");

    fun layerId(): Int = layerIdValue

    fun displayName(): String = displayNameValue

    fun isKnown(): Boolean = this != UNKNOWN

    companion object {
        /** Maps the canonical OSRS location-shape range to its neutral scene layer. */
        @JvmStatic
        fun fromType(type: Int): ObjectCategory =
            OsrsLocShape.fromId(type)
                .map { it.category() }
                .orElse(UNKNOWN)
    }
}
