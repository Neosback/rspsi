package com.rspsi.studio.theme

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt

/**
 * Settings rows for sidebar panels: labels on the left and controls on the right.
 *
 * Layout is driven by table/font metrics so it remains responsive across DPI and
 * font-scale changes without horizontal scrolling.
 */
object SettingRows {
    private const val LABEL_WEIGHT = 0.58f
    private const val PLAIN_LABEL_WEIGHT = 0.30f

    /** Collapsible section; call [end] only when this returns true. */
    @JvmStatic
    fun begin(
        title: String,
        defaultOpen: Boolean,
    ): Boolean {
        val flags = if (defaultOpen) ImGuiTreeNodeFlags.DefaultOpen else 0
        if (!ImGui.collapsingHeader(title, flags)) {
            return false
        }
        if (!beginTable(title, LABEL_WEIGHT)) {
            return false
        }
        return true
    }

    /** Non-collapsible rows table; call [end] only when this returns true. */
    @JvmStatic
    fun beginPlain(id: String): Boolean = beginTable(id, PLAIN_LABEL_WEIGHT)

    @JvmStatic
    fun end() {
        ImGui.endTable()
    }

    @JvmStatic
    fun checkbox(
        label: String,
        value: ImBoolean,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        alignRight(ImGui.getFrameHeight())
        return ImGui.checkbox("##$label", value)
    }

    @JvmStatic
    fun combo(
        label: String,
        value: ImInt,
        items: Array<String>,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        fillControlWidth()
        return ImGui.combo("##$label", value, items)
    }

    @JvmStatic
    fun sliderFloat(
        label: String,
        value: FloatArray,
        min: Float,
        max: Float,
        format: String,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        fillControlWidth()
        return ImGui.sliderFloat("##$label", value, min, max, format)
    }

    @JvmStatic
    fun sliderInt(
        label: String,
        value: IntArray,
        min: Int,
        max: Int,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        fillControlWidth()
        return ImGui.sliderInt("##$label", value, min, max)
    }

    @JvmStatic
    fun inputInt(
        label: String,
        value: ImInt,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        fillControlWidth()
        return ImGui.inputInt("##$label", value, 1, 10)
    }

    /** Right-aligned button in the control column. */
    @JvmStatic
    fun button(
        label: String,
        buttonText: String,
    ): Boolean {
        label(label)
        ImGui.tableNextColumn()
        alignRight(ImGui.calcTextSize(buttonText).x + ImGui.getStyle().getFramePaddingX() * 2.0f)
        return ImGui.button("$buttonText##$label")
    }

    /** Read-only value in the control column. */
    @JvmStatic
    fun value(
        label: String,
        value: String,
    ) {
        label(label)
        ImGui.tableNextColumn()
        ImGui.textWrapped(value)
    }

    /** Greyed placeholder for controls not wired to the renderer yet. */
    @JvmStatic
    fun notImplemented(
        label: String,
        reason: String,
    ) {
        ImGui.tableNextRow()
        ImGui.tableNextColumn()
        ImGui.textDisabled(label)
        ImGui.tableNextColumn()
        ImGui.textDisabled("not implemented")
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(reason)
        }
    }

    private fun beginTable(
        id: String,
        labelWeight: Float,
    ): Boolean {
        if (!ImGui.beginTable("##rows-$id", 2, ImGuiTableFlags.SizingStretchProp)) {
            return false
        }
        ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthStretch, labelWeight)
        ImGui.tableSetupColumn("control", ImGuiTableColumnFlags.WidthStretch, 1.0f - labelWeight)
        return true
    }

    private fun label(label: String) {
        ImGui.tableNextRow()
        ImGui.tableNextColumn()
        ImGui.alignTextToFramePadding()
        ImGui.textWrapped(label)
    }

    private fun fillControlWidth() {
        ImGui.setNextItemWidth(-Float.MIN_VALUE)
    }

    private fun alignRight(width: Float) {
        val offset = ImGui.getContentRegionAvailX() - width
        if (offset > 0.0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + offset)
        }
    }
}
