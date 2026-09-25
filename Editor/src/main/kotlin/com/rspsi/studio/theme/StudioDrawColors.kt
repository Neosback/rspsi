package com.rspsi.studio.theme

/**
 * Converts normal ARGB color ints to Dear ImGui ImDrawList's ABGR packing.
 *
 * ImGui style-color APIs already normalize ARGB and must not use this conversion.
 */
object StudioDrawColors {
    @JvmStatic
    fun abgr(argb: Int): Int {
        val alphaAndGreen = argb and 0xFF00FF00.toInt()
        val red = (argb ushr 16) and 0xFF
        val blue = argb and 0xFF
        return alphaAndGreen or (blue shl 16) or red
    }
}
