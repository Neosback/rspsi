@file:JvmName("OsrsTileFlags")

package com.rspsi.editor.model

/** Neutral names for terrain flags used by the OSRS map codec and authored world model. */

const val BLOCK_MAP_SQUARE: Int = 0x1
const val BRIDGE: Int = 0x2
const val REMOVE_ROOFS: Int = 0x4

/** RuneLite Constants.TILE_FLAG_VIS_BELOW: tile may be visible from a lower scene level. */
const val VIS_BELOW: Int = 0x8

/**
 * Historical alias retained for source/binary compatibility.
 *
 * New code should use [VIS_BELOW]. The alias remains until compatibility callers are removed.
 */
@Deprecated("Use VIS_BELOW")
@field:java.lang.Deprecated
const val MINIMAP_BRIDGE: Int = VIS_BELOW

/** Tile is hidden from the normal minimap scene pass when either bit is set. */
const val MINIMAP_HIDDEN: Int = 0x18

fun hasBridge(flags: Int): Boolean =
    flags and BRIDGE != 0

fun removesRoofs(flags: Int): Boolean =
    flags and REMOVE_ROOFS != 0

fun visibleBelow(flags: Int): Boolean =
    flags and VIS_BELOW != 0

fun isBlocked(flags: Int): Boolean =
    flags and BLOCK_MAP_SQUARE != 0
