package com.rspsi.studio.ui

import com.rspsi.editor.inspector.ObjectReport
import com.rspsi.studio.theme.StudioDrawColors
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags

/**
 * Label/value property grid shared by inspectors so every panel presents
 * sections, labels, values and warnings consistently.
 */
object PropertyGrid {
    private val SECTION = StudioDrawColors.abgr(0xFF38BDF8.toInt())
    private val LABEL = StudioDrawColors.abgr(0xFF94A3B8.toInt())
    private val WARNING = StudioDrawColors.abgr(0xFFF59E0B.toInt())
    private val TITLE = StudioDrawColors.abgr(0xFFE2E8F0.toInt())

    /** Title line, copy action, then each report section. */
    @JvmStatic
    fun render(id: String, report: ObjectReport) {
        ImGui.textColored(TITLE, report.title())
        ImGui.sameLine()
        if (ImGui.smallButton("Copy##$id-copy")) {
            ImGui.setClipboardText(report.toText())
        }

        for (section in report.sections()) {
            ImGui.spacing()
            ImGui.textColored(SECTION, section.title())

            if (
                !ImGui.beginTable(
                    "##$id-${section.title()}",
                    2,
                    ImGuiTableFlags.SizingStretchProp or ImGuiTableFlags.BordersInnerH,
                )
            ) {
                continue
            }

            ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthStretch, 0.36f)
            ImGui.tableSetupColumn("value", ImGuiTableColumnFlags.WidthStretch, 0.64f)

            for (row in section.rows()) {
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                ImGui.textColored(LABEL, row.label())
                ImGui.tableNextColumn()

                if (row.warning()) {
                    ImGui.pushStyleColor(ImGuiCol.Text, WARNING)
                    ImGui.textWrapped(row.value())
                    ImGui.popStyleColor()
                } else {
                    ImGui.textWrapped(row.value())
                }
            }

            ImGui.endTable()
        }
    }
}
