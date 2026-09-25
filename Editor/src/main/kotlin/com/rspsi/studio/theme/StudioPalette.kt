package com.rspsi.studio.theme

/**
 * Semantic color tokens for the native Studio UI.
 *
 * Values use normal ARGB notation. Route a token through [StudioDrawColors.abgr]
 * only when passing it to ImDrawList primitives.
 */
object StudioPalette {
    const val APP_BG: Int = -16051943 // 0xFF0B1119
    const val CHROME_BG: Int = -15722718 // 0xFF101722
    const val PANEL_BG: Int = -15393238 // 0xFF151E2A
    const val PANEL_ELEVATED: Int = -14997706 // 0xFF1B2736
    const val FIELD_BG: Int = -15788512 // 0xFF0F1620
    const val FIELD_HOVER: Int = -14997191 // 0xFF1B2939
    const val BORDER: Int = -14010037 // 0xFF2A394B
    const val BORDER_STRONG: Int = -12956832 // 0xFF3A4B60

    /** Primary brand/action color, matching active Viewer tabs and selected entity labels. */
    const val ACCENT: Int = -15299329 // 0xFF168CFF
    const val ACCENT_HOVER: Int = -12803073 // 0xFF3CA3FF
    const val ACCENT_ACTIVE: Int = -16222497 // 0xFF0876DF
    const val ACCENT_MUTED: Int = -15320482 // 0xFF163A5E
    const val ACCENT_SOFT: Int = -15717563 // 0xFF102B45

    const val TEXT: Int = -788484 // 0xFFF3F7FC
    const val TEXT_MUTED: Int = -5851962 // 0xFFA6B4C6
    const val TEXT_DISABLED: Int = -9338474 // 0xFF718196

    /** Semantic colors are reserved for actual status/state, never general decoration. */
    const val SUCCESS: Int = -13252232 // 0xFF35C978
    const val WARNING: Int = -1004996 // 0xFFF0AA3C
    const val DANGER: Int = -1090200 // 0xFFEF5D68
    const val INFO: Int = -11159809 // 0xFF55B6FF

    @JvmStatic
    fun draw(argb: Int): Int = StudioDrawColors.abgr(argb)
}
