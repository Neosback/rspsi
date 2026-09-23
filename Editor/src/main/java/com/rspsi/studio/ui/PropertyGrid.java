package com.rspsi.studio.ui;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.editor.inspector.ObjectReport;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;

/**
 * Label/value property grid: section headers, muted labels on the left,
 * wrapped values on the right. Shared by inspectors so every panel presents
 * properties the same way.
 */
public final class PropertyGrid {
    private static final int SECTION = StudioDrawColors.abgr(0xFF38BDF8);
    private static final int LABEL = StudioDrawColors.abgr(0xFF94A3B8);
    private static final int WARNING = StudioDrawColors.abgr(0xFFF59E0B);

    private PropertyGrid() {
    }

    /** Title line, a copy button that copies {@link ObjectReport#toText()}, then every section. */
    public static void render(String id, ObjectReport report) {
        ImGui.textColored(StudioDrawColors.abgr(0xFFE2E8F0), report.title());
        ImGui.sameLine();
        if (ImGui.smallButton("Copy##" + id + "-copy")) {
            ImGui.setClipboardText(report.toText());
        }
        for (ObjectReport.Section section : report.sections()) {
            ImGui.spacing();
            ImGui.textColored(SECTION, section.title());
            if (!ImGui.beginTable("##" + id + "-" + section.title(), 2,
                    ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.BordersInnerH)) {
                continue;
            }
            ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthStretch, 0.36f);
            ImGui.tableSetupColumn("value", ImGuiTableColumnFlags.WidthStretch, 0.64f);
            for (ObjectReport.Row row : section.rows()) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.textColored(LABEL, row.label());
                ImGui.tableNextColumn();
                if (row.warning()) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, WARNING);
                    ImGui.textWrapped(row.value());
                    ImGui.popStyleColor();
                } else {
                    ImGui.textWrapped(row.value());
                }
            }
            ImGui.endTable();
        }
    }
}
