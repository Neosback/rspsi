package com.rspsi.studio.theme

/**
 * Semantic color tokens for the native Studio UI.
 *
 * Values use normal ARGB notation. Route a token through [StudioDrawColors.abgr]
 * only when passing it to ImDrawList primitives.
 */
object StudioPalette {
    @JvmField val APP_BG: Int = 0xFF0B1119.toInt()
    @JvmField val CHROME_BG: Int = 0xFF101722.toInt()
    @JvmField val PANEL_BG: Int = 0xFF151E2A.toInt()
    @JvmField val PANEL_ELEVATED: Int = 0xFF1B2736.toInt()
    @JvmField val FIELD_BG: Int = 0xFF0F1620.toInt()
    @JvmField val FIELD_HOVER: Int = 0xFF1B2939.toInt()
    @JvmField val BORDER: Int = 0xFF2A394B.toInt()
    @JvmField val BORDER_STRONG: Int = 0xFF3A4B60.toInt()

    /** Primary brand/action color, matching active Viewer tabs and selected entity labels. */
    @JvmField val ACCENT: Int = 0xFF168CFF.toInt()
    @JvmField val ACCENT_HOVER: Int = 0xFF3CA3FF.toInt()
    @JvmField val ACCENT_ACTIVE: Int = 0xFF0876DF.toInt()
    @JvmField val ACCENT_MUTED: Int = 0xFF163A5E.toInt()
    @JvmField val ACCENT_SOFT: Int = 0xFF102B45.toInt()

    @JvmField val TEXT: Int = 0xFFF3F7FC.toInt()
    @JvmField val TEXT_MUTED: Int = 0xFFA6B4C6.toInt()
    @JvmField val TEXT_DISABLED: Int = 0xFF718196.toInt()

    /** Semantic colors are reserved for actual status/state, never general decoration. */
    @JvmField val SUCCESS: Int = 0xFF35C978.toInt()
    @JvmField val WARNING: Int = 0xFFF0AA3C.toInt()
    @JvmField val DANGER: Int = 0xFFEF5D68.toInt()
    @JvmField val INFO: Int = 0xFF55B6FF.toInt()

    @JvmStatic
    fun draw(argb: Int): Int = StudioDrawColors.abgr(argb)
}
