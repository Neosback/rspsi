package com.rspsi.studio.theme

/**
 * Converts normal ARGB colors to Dear ImGui ImDrawList's packed ABGR representation.
 *
 * ImGui style-color APIs already normalize ARGB and must not use this conversion.
 */
object StudioDrawColors {
    @JvmStatic
    fun abgr(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }
}
