package com.rspsi.studio.ui;

import com.rspsi.editor.plugin.ui.EditorUiNode;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiSliderFlags;
import imgui.type.ImInt;

import java.util.List;

/**
 * Native Studio renderer for the frontend-neutral extension UI tree.
 *
 * <p>This class is intentionally one-way: extensions describe semantic widgets,
 * and Studio owns their actual ImGui projection, styling, IDs, focus behavior,
 * and future accessibility/scaling policy.</p>
 */
public final class DeclarativeToolUiRenderer {

    public void render(EditorUiNode node) {
        if (node != null) render(node, "root");
    }

    private void render(EditorUiNode node, String path) {
        switch (node) {
            case EditorUiNode.Text text -> {
                String value = text.value().get();
                if (text.muted()) ImGui.textDisabled(value == null ? "" : value);
                else ImGui.textWrapped(value == null ? "" : value);
            }
            case EditorUiNode.Button button -> {
                if (ImGui.button(button.label() + "##ext-" + path)) {
                    button.action().run();
                }
            }
            case EditorUiNode.Toggle toggle -> {
                boolean current = toggle.value().getAsBoolean();
                ImGui.alignTextToFramePadding();
                ImGui.text(toggle.label());
                ImGui.sameLine();
                boolean updated = StudioWidgets.toggleSwitch(
                        "ext-" + path, current, current ? "Enabled" : "Disabled");
                if (updated != current) toggle.onChange().accept(updated);
            }
            case EditorUiNode.IntSlider slider -> {
                ImGui.alignTextToFramePadding();
                ImGui.text(slider.label());
                ImGui.sameLine();
                int[] value = {slider.value().getAsInt()};
                ImGui.setNextItemWidth(Math.max(120.0f, ImGui.getContentRegionAvailX()));
                if (ImGui.sliderInt("##ext-" + path, value, slider.minimum(), slider.maximum())) {
                    slider.onChange().accept(value[0]);
                }
            }
            case EditorUiNode.DecimalSlider slider -> {
                ImGui.alignTextToFramePadding();
                ImGui.text(slider.label());
                ImGui.sameLine();
                float[] value = {(float) slider.value().getAsDouble()};
                ImGui.setNextItemWidth(Math.max(120.0f, ImGui.getContentRegionAvailX()));
                if (ImGui.sliderFloat(
                        "##ext-" + path,
                        value,
                        (float) slider.minimum(),
                        (float) slider.maximum(),
                        "%.2f",
                        ImGuiSliderFlags.None)) {
                    slider.onChange().accept(value[0]);
                }
            }
            case EditorUiNode.Select select -> {
                ImGui.alignTextToFramePadding();
                ImGui.text(select.label());
                ImGui.sameLine();
                List<String> options = select.options();
                String current = select.value().get();
                int currentIndex = Math.max(0, options.indexOf(current));
                ImInt selected = new ImInt(currentIndex);
                ImGui.setNextItemWidth(Math.max(120.0f, ImGui.getContentRegionAvailX()));
                if (ImGui.combo("##ext-" + path, selected, options.toArray(String[]::new))) {
                    select.onChange().accept(options.get(selected.get()));
                }
            }
            case EditorUiNode.Section section -> {
                ImGui.textColored(StudioPalette.ACCENT, section.title());
                ImGui.separator();
                renderChildren(section.children(), path + "-section");
            }
            case EditorUiNode.Row row -> renderRow(row.children(), path + "-row");
            case EditorUiNode.Column column -> renderChildren(column.children(), path + "-column");
            case EditorUiNode.Separator ignored -> ImGui.separator();
        }
    }

    private void renderChildren(List<EditorUiNode> children, String path) {
        for (int index = 0; index < children.size(); index++) {
            render(children.get(index), path + "-" + index);
        }
    }

    private void renderRow(List<EditorUiNode> children, String path) {
        for (int index = 0; index < children.size(); index++) {
            if (index > 0) ImGui.sameLine();
            render(children.get(index), path + "-" + index);
        }
    }
}
