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

    // Backgrounds, darkest to lightest in modern layered Zinc/Neutral scale.
    private static final float[] BG_APP = rgb(0x0E, 0x10, 0x15);
    private static final float[] BG_PANEL = rgb(0x14, 0x16, 0x1D);
    private static final float[] BG_PANEL_ALT = rgb(0x1B, 0x1E, 0x27);
    private static final float[] BG_FIELD = rgb(0x11, 0x13, 0x1A);
    private static final float[] BG_FIELD_HOVERED = rgb(0x22, 0x26, 0x33);

    private static final float[] BORDER = rgb(0x27, 0x2C, 0x38);

    // Modern Indigo / Violet primary accents (#6366F1), plus OSRS gold for telemetry/badges
    private static final float[] ACCENT = rgb(0x63, 0x66, 0xF1);
    private static final float[] ACCENT_HOVER = rgb(0x81, 0x8C, 0xF8);
    private static final float[] ACCENT_ACTIVE = rgb(0x4F, 0x46, 0xE5);
    private static final float[] ACCENT_MUTED = rgb(0x25, 0x28, 0x3D);
    private static final float[] ACCENT_GOLD = rgb(0xF5, 0x9E, 0x0B);

    private static final float[] TEXT = rgb(0xF1, 0xF5, 0xF9);
    private static final float[] TEXT_MUTED = rgb(0x94, 0xA3, 0xB8);
    private static final float[] TEXT_DISABLED = rgb(0x64, 0x74, 0x8B);

    /** Applies the theme once. Call after {@code ImGui.createContext()}. */
    public static void apply() {
        // Start from the stock dark palette so every ImGuiCol slot this
        // theme does not explicitly override still has a sane, readable
        // value instead of defaulting to ImGui's light theme.
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();

        // Modern SaaS-grade border radii (Linear / Raycast aesthetic)
        style.setWindowRounding(10.0f);
        style.setChildRounding(8.0f);
        style.setFrameRounding(6.0f);
        style.setPopupRounding(8.0f);
        style.setScrollbarRounding(10.0f);
        style.setGrabRounding(6.0f);
        style.setTabRounding(6.0f);
        style.setWindowBorderSize(1.0f);
        style.setChildBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);

        // Generous spacing & whitespace: components have room to breathe
        style.setWindowPadding(14.0f, 12.0f);
        style.setFramePadding(10.0f, 6.0f);
        style.setItemSpacing(8.0f, 8.0f);
        style.setItemInnerSpacing(6.0f, 6.0f);
        style.setIndentSpacing(18.0f);
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
        color(style, ImGuiCol.ScrollbarGrab, BG_PANEL_ALT);
        color(style, ImGuiCol.ScrollbarGrabHovered, BG_FIELD_HOVERED);
        color(style, ImGuiCol.ScrollbarGrabActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.CheckMark, ACCENT);
        color(style, ImGuiCol.SliderGrab, ACCENT);
        color(style, ImGuiCol.SliderGrabActive, ACCENT_ACTIVE);

        // Buttons stay quiet and layered until interacted with
        color(style, ImGuiCol.Button, BG_PANEL_ALT);
        color(style, ImGuiCol.ButtonHovered, rgb(0x25, 0x2A, 0x38));
        color(style, ImGuiCol.ButtonActive, ACCENT);

        color(style, ImGuiCol.Header, ACCENT_MUTED, 0.85f);
        color(style, ImGuiCol.HeaderHovered, rgb(0x28, 0x2F, 0x42));
        color(style, ImGuiCol.HeaderActive, ACCENT_ACTIVE);

        color(style, ImGuiCol.Separator, BORDER, 0.85f);
        color(style, ImGuiCol.SeparatorHovered, ACCENT);
        color(style, ImGuiCol.SeparatorActive, ACCENT_ACTIVE);

        // Visible focus rings: resize grips use the accent
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
        color(style, ImGuiCol.TableRowBgAlt, BG_PANEL_ALT, 0.35f);

        color(style, ImGuiCol.TextLink, ACCENT_HOVER);
        color(style, ImGuiCol.TextSelectedBg, ACCENT_MUTED);

        color(style, ImGuiCol.NavCursor, ACCENT);
        color(style, ImGuiCol.DragDropTarget, ACCENT);
        color(style, ImGuiCol.UnsavedMarker, ACCENT_GOLD);
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
