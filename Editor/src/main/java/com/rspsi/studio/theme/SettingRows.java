package com.rspsi.studio.theme;

import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Settings rows for sidebar panels: label left-aligned, control right-aligned
 * (checkboxes flush right, combos and fields filling the right column).
 *
 * <p>Each section is a two-column stretch table, so rows always fit the panel
 * width and never need horizontal scrolling; long labels wrap. No pixel
 * constants - sizes derive from the current font/frame metrics, so the layout
 * holds at any DPI or font scale.</p>
 */
public final class SettingRows {
    private static final float LABEL_WEIGHT = 0.58f;

    private SettingRows() {
    }

    /** Collapsible section; call {@link #end()} only when this returns true. */
    public static boolean begin(String title, boolean defaultOpen) {
        if (!ImGui.collapsingHeader(title, defaultOpen ? ImGuiTreeNodeFlags.DefaultOpen : 0)) {
            return false;
        }
        if (!ImGui.beginTable("##rows-" + title, 2, ImGuiTableFlags.SizingStretchProp)) {
            return false;
        }
        ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthStretch, LABEL_WEIGHT);
        ImGui.tableSetupColumn("control", ImGuiTableColumnFlags.WidthStretch, 1.0f - LABEL_WEIGHT);
        return true;
    }

    /**
     * A rows table without a collapsing header, for a few short labels (the
     * control column gets most of the width); call {@link #end()} only when
     * this returns true.
     */
    public static boolean beginPlain(String id) {
        if (!ImGui.beginTable("##rows-" + id, 2, ImGuiTableFlags.SizingStretchProp)) {
            return false;
        }
        ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthStretch, 0.3f);
        ImGui.tableSetupColumn("control", ImGuiTableColumnFlags.WidthStretch, 0.7f);
        return true;
    }

    public static void end() {
        ImGui.endTable();
    }

    public static boolean checkbox(String label, ImBoolean value) {
        label(label);
        ImGui.tableNextColumn();
        alignRight(ImGui.getFrameHeight());
        return ImGui.checkbox("##" + label, value);
    }

    public static boolean combo(String label, ImInt value, String[] items) {
        label(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        return ImGui.combo("##" + label, value, items);
    }

    public static boolean sliderFloat(String label, float[] value, float min, float max, String format) {
        label(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        return ImGui.sliderFloat("##" + label, value, min, max, format);
    }

    public static boolean sliderInt(String label, int[] value, int min, int max) {
        label(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        return ImGui.sliderInt("##" + label, value, min, max);
    }

    public static boolean inputInt(String label, ImInt value) {
        label(label);
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        return ImGui.inputInt("##" + label, value, 1, 10);
    }

    /** Right-aligned button in the control column. */
    public static boolean button(String label, String buttonText) {
        label(label);
        ImGui.tableNextColumn();
        alignRight(ImGui.calcTextSize(buttonText).x + ImGui.getStyle().getFramePaddingX() * 2.0f);
        return ImGui.button(buttonText + "##" + label);
    }

    /** Read-only value, right-aligned and wrapped. */
    public static void value(String label, String value) {
        label(label);
        ImGui.tableNextColumn();
        ImGui.textWrapped(value);
    }

    /** A control that is not wired to the renderer yet: shown greyed with the reason on hover. */
    public static void notImplemented(String label, String reason) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.textDisabled("not implemented");
        if (ImGui.isItemHovered()) ImGui.setTooltip(reason);
    }

    private static void label(String label) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(label);
    }

    private static void alignRight(float width) {
        float offset = ImGui.getContentRegionAvailX() - width;
        if (offset > 0.0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
    }
}
