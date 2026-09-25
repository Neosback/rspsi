package com.rspsi.studio.theme;

/**
 * Semantic color tokens for the native Studio UI.
 *
 * <p>Values use normal ARGB notation. Route a token through
 * {@link StudioDrawColors#abgr(int)} only when passing it to ImDrawList primitives.</p>
 */
public final class StudioPalette {
    private StudioPalette() {}

    public static final int APP_BG = 0xFF0B1119;
    public static final int CHROME_BG = 0xFF101722;
    public static final int PANEL_BG = 0xFF151E2A;
    public static final int PANEL_ELEVATED = 0xFF1B2736;
    public static final int FIELD_BG = 0xFF0F1620;
    public static final int FIELD_HOVER = 0xFF1B2939;
    public static final int BORDER = 0xFF2A394B;
    public static final int BORDER_STRONG = 0xFF3A4B60;

    /** Primary brand/action color, matching active Viewer tabs and selected entity labels. */
    public static final int ACCENT = 0xFF168CFF;
    public static final int ACCENT_HOVER = 0xFF3CA3FF;
    public static final int ACCENT_ACTIVE = 0xFF0876DF;
    public static final int ACCENT_MUTED = 0xFF163A5E;
    public static final int ACCENT_SOFT = 0xFF102B45;

    public static final int TEXT = 0xFFF3F7FC;
    public static final int TEXT_MUTED = 0xFFA6B4C6;
    public static final int TEXT_DISABLED = 0xFF718196;

    /** Semantic colors are reserved for actual status/state, never general decoration. */
    public static final int SUCCESS = 0xFF35C978;
    public static final int WARNING = 0xFFF0AA3C;
    public static final int DANGER = 0xFFEF5D68;
    public static final int INFO = 0xFF55B6FF;

    public static int draw(int argb) {
        return StudioDrawColors.abgr(argb);
    }
}
