package com.rspsi.editor.core.settings

import com.rspsi.editor.plugin.EditorSetting
import com.rspsi.editor.settings.EditorSettingKeys
import com.rspsi.editor.settings.SettingsStore
import com.rspsi.editor.tool.DuplicateSelectionTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.MoveSelectionTool
import com.rspsi.editor.tool.ReplaceSelectionTool
import com.rspsi.editor.tool.RotateSelectionTool

/**
 * Shared selection/transform state projected into any frontend's controls.
 *
 * This is a direct behavioral migration of the Java implementation. The backing [SettingsStore]
 * remains the source of truth and configuration reads one immutable snapshot before dispatching
 * to a concrete tool.
 */
class SelectionToolSettings(
    settings: SettingsStore?,
) {
    private val settings: SettingsStore =
        settings ?: throw NullPointerException("settings")

    fun settings(): List<EditorSetting> =
        java.util.List.of(
            EditorSetting.from(
                settings,
                EditorSettingKeys.SELECTION_QUARTER_TURNS,
            ),
            EditorSetting.from(
                settings,
                EditorSettingKeys.SELECTION_SNAP_GRID,
            ),
            EditorSetting.from(
                settings,
                EditorSettingKeys.SELECTION_REPLACEMENT_ID,
            ),
        )

    fun configure(
        toolId: String?,
        tool: EditorTool?,
    ) {
        val values = settings.snapshot()

        if (toolId == null) {
            // Java string-switch behavior for a null selector is an NPE after snapshot capture.
            throw NullPointerException()
        }

        when (toolId) {
            "selection.move" ->
                (tool as MoveSelectionTool).setSnapGridSize(
                    values.get(EditorSettingKeys.SELECTION_SNAP_GRID),
                )

            "selection.rotate" ->
                (tool as RotateSelectionTool).setQuarterTurns(
                    values.get(EditorSettingKeys.SELECTION_QUARTER_TURNS),
                )

            "selection.duplicate" ->
                (tool as DuplicateSelectionTool).setSnapGridSize(
                    values.get(EditorSettingKeys.SELECTION_SNAP_GRID),
                )

            "selection.replace" ->
                (tool as ReplaceSelectionTool).setReplacementId(
                    values.get(EditorSettingKeys.SELECTION_REPLACEMENT_ID),
                )

            "selection.box",
            "selection.lasso",
            "selection.attribute",
            -> Unit

            else ->
                throw IllegalArgumentException(
                    "Unknown selection tool: $toolId",
                )
        }
    }
}
