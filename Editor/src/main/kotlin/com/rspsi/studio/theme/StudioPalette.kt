package com.rspsi.studio.theme

/**
 * Semantic color tokens for the native Studio UI.
 *
 * Values use normal ARGB notation. Route a token through [StudioDrawColors.abgr]
 * only when passing it to ImDrawList primitives.
 */
object StudioPalette {
    const val APP_BG: Int = 0xFF0B0F14.toInt()
    const val CHROME_BG: Int = 0xFF0F151C.toInt()
    const val PANEL_BG: Int = 0xFF131B24.toInt()
    const val PANEL_ELEVATED: Int = 0xFF19232F.toInt()
    const val FIELD_BG: Int = 0xFF0C1218.toInt()
    const val FIELD_HOVER: Int = 0xFF16202B.toInt()
    const val BORDER: Int = 0xFF22303F.toInt()
    const val BORDER_STRONG: Int = 0xFF32455A.toInt()

    /** Primary brand/action color matching the OpenRune cyan logo. */
    const val ACCENT: Int = 0xFF00CED1.toInt()
    const val ACCENT_HOVER: Int = 0xFF38E7F2.toInt()
    const val ACCENT_ACTIVE: Int = 0xFF009FA8.toInt()
    const val ACCENT_MUTED: Int = 0xFF0B3F4A.toInt()
    const val ACCENT_SOFT: Int = 0xFF082C34.toInt()

    const val TEXT: Int = 0xFFF1F5F9.toInt()
    const val TEXT_MUTED: Int = 0xFF94A3B8.toInt()
    const val TEXT_DISABLED: Int = 0xFF5F7186.toInt()

    /** Semantic colors are reserved for actual status/state, never general decoration. */
    const val SUCCESS: Int = 0xFF22C55E.toInt()
    const val WARNING: Int = 0xFFF59E0B.toInt()
    const val DANGER: Int = 0xFFEF4444.toInt()
    const val INFO: Int = 0xFF38BDF8.toInt()

    @JvmStatic
    fun draw(argb: Int): Int = StudioDrawColors.abgr(argb)

    @JvmStatic
    fun u32(argb: Int): Int = StudioDrawColors.abgr(argb)
}
