package com.rspsi.studio.theme;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;

/** Small, consistent controls used by the native OpenRune Studio shell. */
public final class StudioWidgets {
    private StudioWidgets() {
    }

    public static boolean iconButton(String id, String icon, String label, String shortcut) {
        boolean clicked = ImGui.button(icon + "  " + label + "##" + id);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip(label + (shortcut == null || shortcut.isBlank()
                    ? "" : "  (" + shortcut + ")"));
        }
        return clicked;
    }

    public static boolean railButton(String id, String icon, String label, boolean selected,
                                     String shortcut) {
        // Rails are permanent chrome. Draw the icon explicitly over an
        // invisible hit target so narrow dock content cannot clip the glyph.
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float height = 36.0f;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        boolean hovered = ImGui.isItemHovered();

        ImDrawList drawList = ImGui.getWindowDrawList();
        if (selected || hovered) {
            int background = ImGui.getColorU32(selected
                    ? 0.18f : 0.12f, selected ? 0.36f : 0.16f,
                    selected ? 0.58f : 0.21f, selected ? 1.0f : 0.9f);
            drawList.addRectFilled(x, y, x + width, y + height, background, 3.0f);
        }
        if (selected) {
            drawList.addRectFilled(x, y, x + 3.0f, y + height,
                    ImGui.getColorU32(0.24f, 0.56f, 0.90f, 1.0f), 2.0f);
        }

        ImGui.pushFont(StudioFonts.icon(), 1.0f);
        ImVec2 glyphSize = ImGui.calcTextSize(icon);
        ImGui.popFont();
        drawList.addText(StudioFonts.icon(),
                18,
                x + Math.max(0.0f, (width - glyphSize.x) * 0.5f),
                y + Math.max(0.0f, (height - glyphSize.y) * 0.5f),
                ImGui.getColorU32(0.86f, 0.89f, 0.94f, 1.0f), icon);

        if (hovered) {
            ImGui.setItemTooltip(label + (shortcut == null || shortcut.isBlank()
                    ? "" : "  (" + shortcut + ")"));
        }
        return clicked;
    }

    public static void heading(String label, String hint) {
        section(label);
        if (hint != null && !hint.isBlank()) ImGui.textDisabled(hint);
    }

    /** Modern editor section label: typography first, divider second. */
    public static void section(String label) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.56f, 0.60f, 0.67f, 1.0f);
        ImGui.text(label.toUpperCase());
        ImGui.popStyleColor();
        ImGui.separator();
    }

    public static void badge(String label, float r, float g, float b) {
        ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 0.22f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, 0.32f);
        ImGui.smallButton(label);
        ImGui.popStyleColor(2);
    }

    public static boolean toggle(String label, boolean value) {
        ImBoolean state = new ImBoolean(value);
        return ImGui.checkbox(label, state) ? state.get() : value;
    }

    public static void searchHint(String hint) {
        ImGui.textDisabled(StudioIcons.SEARCH + "  " + hint);
    }

    public static void info(String text) {
        ImGui.textDisabled(StudioIcons.INFO + "  " + text);
    }
}
