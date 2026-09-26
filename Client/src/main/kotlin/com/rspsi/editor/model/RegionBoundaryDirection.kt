package com.rspsi.editor.model

/**
 * Shared OSRS region edges checked by [WorldRegionWindow].
 *
 * Only EAST and NORTH are needed because each shared boundary is evaluated once from the
 * lower/south-west owning region rather than duplicated from both neighboring regions.
 */
enum class RegionBoundaryDirection {
    EAST,
    NORTH,
}
