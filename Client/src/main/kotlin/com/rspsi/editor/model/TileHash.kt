package com.rspsi.editor.model

/**
 * The hash every tile-coordinate type uses, so tile-keyed maps stay O(1).
 *
 * A data class's generated hash, `(plane * 31 + x) * 31 + y`, maps the ~160k tiles of a
 * 3x3 region window onto a few thousand values. `Map.copyOf` resolves collisions by linear
 * probing, so region loads spent most of the window-scene build walking probe chains
 * (profiled 2026-09-27: 75% of `RenderWindowSceneBuilder.build`).
 *
 * Packing then mixing is collision-free for every plane below 16 and x, y below 16384 (the
 * whole OSRS world): both the pack and the golden-ratio multiply plus xor-shift are
 * bijections on 32 bits. The mix spreads neighbouring tiles across the table.
 */
internal object TileHash {
    @JvmStatic
    fun of(plane: Int, x: Int, y: Int): Int {
        var h = (plane shl 28) or ((x and 0x3FFF) shl 14) or (y and 0x3FFF)
        h *= -0x61C88647 // 0x9E3779B9, the 32-bit golden ratio
        return h xor (h ushr 16)
    }
}
