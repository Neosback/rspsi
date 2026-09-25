package com.rspsi.studio.ui

import com.rspsi.editor.plugin.ui.EditorUiNode
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImGui
import imgui.flag.ImGuiSliderFlags
import imgui.type.ImInt

/**
 * Native Studio renderer for the frontend-neutral extension UI tree.
 *
 * Extensions describe semantic widgets; Studio owns their ImGui projection,
 * styling, IDs, focus behavior, and future accessibility/scaling policy.
 */
class DeclarativeToolUiRenderer {
    fun render(node: EditorUiNode?) {
        if (node != null) {
            render(node, "root")
        }
    }

    private fun render(node: EditorUiNode, path: String) {
        when (node) {
            is EditorUiNode.Text -> {
                val value = node.value().get().orEmpty()
                if (node.muted()) {
                    ImGui.textDisabled(value)
                } else {
                    ImGui.textWrapped(value)
                }
            }

            is EditorUiNode.Button -> {
                if (ImGui.button("${node.label()}##ext-$path")) {
                    node.action().run()
                }
            }

            is EditorUiNode.Toggle -> {
                val current = node.value().asBoolean
                ImGui.alignTextToFramePadding()
                ImGui.text(node.label())
                ImGui.sameLine()

                val updated =
                    StudioWidgets.toggleSwitch(
                        "ext-$path",
                        current,
                        if (current) "Enabled" else "Disabled",
                    )
                if (updated != current) {
                    node.onChange().accept(updated)
                }
            }

            is EditorUiNode.IntSlider -> {
                ImGui.alignTextToFramePadding()
                ImGui.text(node.label())
                ImGui.sameLine()

                val value = intArrayOf(node.value().asInt)
                ImGui.setNextItemWidth(kotlin.math.max(MIN_CONTROL_WIDTH, ImGui.getContentRegionAvailX()))
                if (ImGui.sliderInt("##ext-$path", value, node.minimum(), node.maximum())) {
                    node.onChange().accept(value[0])
                }
            }

            is EditorUiNode.DecimalSlider -> {
                ImGui.alignTextToFramePadding()
                ImGui.text(node.label())
                ImGui.sameLine()

                val value = floatArrayOf(node.value().asDouble.toFloat())
                ImGui.setNextItemWidth(kotlin.math.max(MIN_CONTROL_WIDTH, ImGui.getContentRegionAvailX()))
                if (
                    ImGui.sliderFloat(
                        "##ext-$path",
                        value,
                        node.minimum().toFloat(),
                        node.maximum().toFloat(),
                        "%.2f",
                        ImGuiSliderFlags.None,
                    )
                ) {
                    node.onChange().accept(value[0].toDouble())
                }
            }

            is EditorUiNode.Select -> {
                ImGui.alignTextToFramePadding()
                ImGui.text(node.label())
                ImGui.sameLine()

                val options = node.options()
                val selected =
                    ImInt(
                        options
                            .indexOf(node.value().get())
                            .coerceAtLeast(0),
                    )
                ImGui.setNextItemWidth(kotlin.math.max(MIN_CONTROL_WIDTH, ImGui.getContentRegionAvailX()))
                if (ImGui.combo("##ext-$path", selected, options.toTypedArray())) {
                    node.onChange().accept(options[selected.get()])
                }
            }

            is EditorUiNode.Section -> {
                ImGui.textColored(StudioPalette.ACCENT, node.title())
                ImGui.separator()
                renderChildren(node.children(), "$path-section")
            }

            is EditorUiNode.Row -> renderRow(node.children(), "$path-row")
            is EditorUiNode.Column -> renderChildren(node.children(), "$path-column")
            is EditorUiNode.Separator -> ImGui.separator()
        }
    }

    private fun renderChildren(
        children: List<EditorUiNode>,
        path: String,
    ) {
        children.forEachIndexed { index, child ->
            render(child, "$path-$index")
        }
    }

    private fun renderRow(
        children: List<EditorUiNode>,
        path: String,
    ) {
        children.forEachIndexed { index, child ->
            if (index > 0) {
                ImGui.sameLine()
            }
            render(child, "$path-$index")
        }
    }

    companion object {
        private const val MIN_CONTROL_WIDTH = 120.0f
    }
}
