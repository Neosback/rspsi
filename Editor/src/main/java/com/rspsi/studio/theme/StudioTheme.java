package com.rspsi.studio.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

/**
 * One-time Dear ImGui style pass for the native OpenRune Studio shell.
 *
 * <p>Implements the theme rules recorded in {@code docs/UI_UX_FOUNDATION.md}:
 * dark viewport/panel separation, a restrained accent color reserved for
 * active/selected state, visible focus rings, and low corner rounding in the
 * spirit of dense technical tools rather than a rounded consumer application.
 * Controls are deliberately flat; hierarchy comes from spacing, typography,
 * and quiet surfaces instead of stock ImGui bevels.</p>
 */
public final class StudioTheme {
    private StudioTheme() {
    }

    // Backgrounds, darkest to lightest in soft modern graphite slate (#2E3035).
    private static final float[] BG_APP = rgb(0x2E, 0x30, 0x35);
    private static final float[] BG_PANEL = rgb(0x26, 0x28, 0x2B);
    private static final float[] BG_PANEL_ALT = rgb(0x32, 0x35, 0x3B);
    private static final float[] BG_FIELD = rgb(0x22, 0x23, 0x26);
    private static final float[] BG_FIELD_HOVERED = rgb(0x35, 0x38, 0x40);

    private static final float[] BORDER = rgb(0x44, 0x4A, 0x54);

    // Warm OSRS gold and bronze accents (#D49B35)
    private static final float[] ACCENT = rgb(0xD4, 0x9B, 0x35);
    private static final float[] ACCENT_HOVER = rgb(0xF5, 0xC4, 0x69);
    private static final float[] ACCENT_ACTIVE = rgb(0xB8, 0x82, 0x28);
    private static final float[] ACCENT_MUTED = rgb(0x3D, 0x42, 0x4D);
    private static final float[] ACCENT_GOLD = rgb(0xD4, 0x9B, 0x35);

    private static final float[] TEXT = rgb(0xEB, 0xEE, 0xF2);
    private static final float[] TEXT_MUTED = rgb(0x9E, 0xA8, 0xB6);
    private static final float[] TEXT_DISABLED = rgb(0x6E, 0x77, 0x85);

    /** Applies the theme once. Call after {@code ImGui.createContext()}. */
    public static void apply() {
        // Start from the stock dark palette so every ImGuiCol slot this
        // theme does not explicitly override still has a sane, readable
        // value instead of defaulting to ImGui's light theme.
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();

        // Modern softer rounding for a refined IDE feel
        style.setWindowRounding(6.0f);
        style.setChildRounding(4.0f);
        style.setFrameRounding(4.0f);
        style.setPopupRounding(6.0f);
        style.setScrollbarRounding(6.0f);
        style.setGrabRounding(4.0f);
        style.setTabRounding(5.0f);
        style.setWindowBorderSize(0.0f);
        style.setChildBorderSize(0.0f);
        style.setPopupBorderSize(1.0f);
        style.setFrameBorderSize(0.0f);
        style.setWindowPadding(8.0f, 6.0f);
        style.setFramePadding(6.0f, 4.0f);
        style.setItemSpacing(6.0f, 4.0f);
        style.setItemInnerSpacing(5.0f, 3.0f);
        style.setIndentSpacing(16.0f);
        style.setScrollbarSize(10.0f);

        color(style, ImGuiCol.Text, TEXT);
        color(style, ImGuiCol.TextDisabled, TEXT_DISABLED);
        color(style, ImGuiCol.WindowBg, BG_APP);
        color(style, ImGuiCol.ChildBg, BG_PANEL);
        color(style, ImGuiCol.PopupBg, BG_PANEL_ALT);
        color(style, ImGuiCol.Border, BORDER);
        color(style, ImGuiCol.BorderShadow, BG_APP, 0.0f);

        color(style, ImGuiCol.FrameBg, BG_FIELD);
        color(style, ImGuiCol.FrameBgHovered, BG_FIELD_HOVERED);
        color(style, ImGuiCol.FrameBgActive, ACCENT_MUTED);

        color(style, ImGuiCol.TitleBg, BG_APP);
        color(style, ImGuiCol.TitleBgActive, BG_PANEL_ALT);
        color(style, ImGuiCol.TitleBgCollapsed, BG_APP, 0.6f);
        color(style, ImGuiCol.MenuBarBg, BG_PANEL);

        color(style, ImGuiCol.ScrollbarBg, BG_APP);
        color(style, ImGuiCol.ScrollbarGrab, BG_FIELD);
        color(style, ImGuiCol.ScrollbarGrabHovered, BG_FIELD_HOVERED);
        color(style, ImGuiCol.ScrollbarGrabActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.CheckMark, ACCENT);
        color(style, ImGuiCol.SliderGrab, ACCENT);
        color(style, ImGuiCol.SliderGrabActive, ACCENT_ACTIVE);

        // Buttons stay neutral until interacted with; the accent is earned
        // by hover/press/selection, not painted on every control by default.
        color(style, ImGuiCol.Button, BG_PANEL_ALT);
        color(style, ImGuiCol.ButtonHovered, ACCENT_MUTED);
        color(style, ImGuiCol.ButtonActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.Header, ACCENT_MUTED, 0.72f);
        color(style, ImGuiCol.HeaderHovered, ACCENT_MUTED);
        color(style, ImGuiCol.HeaderActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.Separator, BORDER, 0.75f);
        color(style, ImGuiCol.SeparatorHovered, ACCENT);
        color(style, ImGuiCol.SeparatorActive, ACCENT_ACTIVE);

        // Visible focus rings: resize grips use the accent instead of
        // fading into the panel background.
        color(style, ImGuiCol.ResizeGrip, ACCENT_MUTED, 0.4f);
        color(style, ImGuiCol.ResizeGripHovered, ACCENT_HOVER);
        color(style, ImGuiCol.ResizeGripActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.Tab, BG_PANEL);
        color(style, ImGuiCol.TabHovered, ACCENT_HOVER);
        color(style, ImGuiCol.TabSelected, ACCENT_MUTED);
        color(style, ImGuiCol.TabDimmed, BG_APP);
        color(style, ImGuiCol.TabDimmedSelected, BG_PANEL_ALT);

        color(style, ImGuiCol.DockingPreview, ACCENT, 0.5f);
        color(style, ImGuiCol.DockingEmptyBg, BG_APP);

        color(style, ImGuiCol.TableHeaderBg, BG_PANEL_ALT);
        color(style, ImGuiCol.TableBorderStrong, BORDER);
        color(style, ImGuiCol.TableBorderLight, BORDER, 0.6f);
        color(style, ImGuiCol.TableRowBg, BG_PANEL, 0.0f);
        color(style, ImGuiCol.TableRowBgAlt, BG_PANEL_ALT, 0.4f);

        color(style, ImGuiCol.TextLink, ACCENT);
        color(style, ImGuiCol.TextSelectedBg, ACCENT_MUTED);

        color(style, ImGuiCol.NavCursor, ACCENT);
        color(style, ImGuiCol.DragDropTarget, ACCENT);
        color(style, ImGuiCol.UnsavedMarker, rgb(0xD9, 0xA4, 0x41));
    }

    private static void color(ImGuiStyle style, int slot, float[] rgb) {
        color(style, slot, rgb, 1.0f);
    }

    private static void color(ImGuiStyle style, int slot, float[] rgb, float alpha) {
        style.setColor(slot, rgb[0], rgb[1], rgb[2], alpha);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255.0f, g / 255.0f, b / 255.0f};
    }
}
