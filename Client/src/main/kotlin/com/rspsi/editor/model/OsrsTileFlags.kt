package com.rspsi.editor.model

/**
 * Neutral names for terrain flags used by the OSRS map codec and authored world model.
 *
 * The private constructor preserves utility-class semantics while the companion keeps the
 * existing `OsrsTileFlags.CONSTANT` / `OsrsTileFlags.method(...)` call shape for Kotlin.
 * Constants remain Java static finals and predicate functions remain true Java statics.
 */
class OsrsTileFlags private constructor() {
    companion object {
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

        @JvmStatic
        fun hasBridge(flags: Int): Boolean =
            flags and BRIDGE != 0

        @JvmStatic
        fun removesRoofs(flags: Int): Boolean =
            flags and REMOVE_ROOFS != 0

        @JvmStatic
        fun visibleBelow(flags: Int): Boolean =
            flags and VIS_BELOW != 0

        @JvmStatic
        fun isBlocked(flags: Int): Boolean =
            flags and BLOCK_MAP_SQUARE != 0
    }
}
