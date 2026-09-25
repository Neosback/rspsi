package com.rspsi.studio.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

/**
 * Single authoritative Dear ImGui theme for OpenRune Content Studio.
 *
 * <p>The shell uses one blue interaction language throughout the product.
 * Green/amber/red are reserved for semantic success/warning/error states.
 * Individual panels should consume {@link StudioPalette} instead of inventing
 * local purple/cyan/red variants.</p>
 */
public final class StudioTheme {
    private StudioTheme() {}

    /** Applies the theme once after {@code ImGui.createContext()}. */
    public static void apply() {
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();

        // Dense desktop/web-app geometry. Rounded enough to read as modern,
        // without turning technical panels into floating consumer cards.
        style.setWindowRounding(8.0f);
        style.setChildRounding(6.0f);
        style.setFrameRounding(5.0f);
        style.setPopupRounding(7.0f);
        style.setScrollbarRounding(7.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setWindowBorderSize(1.0f);
        style.setChildBorderSize(1.0f);
        style.setPopupBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);

        style.setWindowPadding(12.0f, 10.0f);
        style.setFramePadding(9.0f, 5.0f);
        style.setItemSpacing(8.0f, 7.0f);
        style.setItemInnerSpacing(6.0f, 5.0f);
        style.setIndentSpacing(18.0f);

        // Scrollbars are intentionally visible. A hidden-dark grab made panels
        // feel broken even when vertical scrolling was correct.
        style.setScrollbarSize(12.0f);

        color(style, ImGuiCol.Text, StudioPalette.TEXT);
        color(style, ImGuiCol.TextDisabled, StudioPalette.TEXT_DISABLED);
        color(style, ImGuiCol.WindowBg, StudioPalette.APP_BG);
        color(style, ImGuiCol.ChildBg, StudioPalette.PANEL_BG);
        color(style, ImGuiCol.PopupBg, StudioPalette.PANEL_ELEVATED);
        color(style, ImGuiCol.Border, StudioPalette.BORDER);
        color(style, ImGuiCol.BorderShadow, 0x00000000);

        color(style, ImGuiCol.FrameBg, StudioPalette.FIELD_BG);
        color(style, ImGuiCol.FrameBgHovered, StudioPalette.FIELD_HOVER);
        color(style, ImGuiCol.FrameBgActive, StudioPalette.ACCENT_SOFT);

        color(style, ImGuiCol.TitleBg, StudioPalette.CHROME_BG);
        color(style, ImGuiCol.TitleBgActive, StudioPalette.PANEL_ELEVATED);
        color(style, ImGuiCol.TitleBgCollapsed, StudioPalette.CHROME_BG);
        color(style, ImGuiCol.MenuBarBg, StudioPalette.CHROME_BG);

        color(style, ImGuiCol.ScrollbarBg, StudioPalette.APP_BG);
        color(style, ImGuiCol.ScrollbarGrab, StudioPalette.BORDER_STRONG);
        color(style, ImGuiCol.ScrollbarGrabHovered, 0xFF52677F);
        color(style, ImGuiCol.ScrollbarGrabActive, StudioPalette.ACCENT);

        color(style, ImGuiCol.CheckMark, StudioPalette.ACCENT);
        color(style, ImGuiCol.SliderGrab, StudioPalette.ACCENT);
        color(style, ImGuiCol.SliderGrabActive, StudioPalette.ACCENT_HOVER);

        color(style, ImGuiCol.Button, StudioPalette.PANEL_ELEVATED);
        color(style, ImGuiCol.ButtonHovered, 0xFF24364A);
        color(style, ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);

        color(style, ImGuiCol.Header, StudioPalette.ACCENT_SOFT);
        color(style, ImGuiCol.HeaderHovered, StudioPalette.ACCENT_MUTED);
        color(style, ImGuiCol.HeaderActive, StudioPalette.ACCENT_ACTIVE);

        color(style, ImGuiCol.Separator, StudioPalette.BORDER);
        color(style, ImGuiCol.SeparatorHovered, StudioPalette.ACCENT);
        color(style, ImGuiCol.SeparatorActive, StudioPalette.ACCENT_ACTIVE);

        color(style, ImGuiCol.ResizeGrip, StudioPalette.BORDER_STRONG);
        color(style, ImGuiCol.ResizeGripHovered, StudioPalette.ACCENT_HOVER);
        color(style, ImGuiCol.ResizeGripActive, StudioPalette.ACCENT_ACTIVE);

        color(style, ImGuiCol.Tab, StudioPalette.CHROME_BG);
        color(style, ImGuiCol.TabHovered, StudioPalette.ACCENT_MUTED);
        color(style, ImGuiCol.TabSelected, StudioPalette.ACCENT);
        color(style, ImGuiCol.TabDimmed, StudioPalette.APP_BG);
        color(style, ImGuiCol.TabDimmedSelected, StudioPalette.PANEL_ELEVATED);

        color(style, ImGuiCol.DockingPreview, withAlpha(StudioPalette.ACCENT, 0x80));
        color(style, ImGuiCol.DockingEmptyBg, StudioPalette.APP_BG);

        color(style, ImGuiCol.TableHeaderBg, StudioPalette.PANEL_ELEVATED);
        color(style, ImGuiCol.TableBorderStrong, StudioPalette.BORDER);
        color(style, ImGuiCol.TableBorderLight, withAlpha(StudioPalette.BORDER, 0x99));
        color(style, ImGuiCol.TableRowBg, 0x00000000);
        color(style, ImGuiCol.TableRowBgAlt, 0x331B2736);

        color(style, ImGuiCol.TextLink, StudioPalette.ACCENT_HOVER);
        color(style, ImGuiCol.TextSelectedBg, withAlpha(StudioPalette.ACCENT, 0x55));

        color(style, ImGuiCol.NavCursor, StudioPalette.ACCENT);
        color(style, ImGuiCol.DragDropTarget, StudioPalette.ACCENT);
        color(style, ImGuiCol.UnsavedMarker, StudioPalette.WARNING);

        // All modal windows dim the owning workspace like the command palette.
        color(style, ImGuiCol.ModalWindowDimBg, 0x99070B11);
        color(style, ImGuiCol.NavWindowingDimBg, 0x66070B11);
    }

    private static void color(ImGuiStyle style, int slot, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        style.setColor(slot, r, g, b, a);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
