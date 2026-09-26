package com.rspsi.editor.core.settings

import com.rspsi.editor.plugin.EditorSetting
import com.rspsi.editor.settings.EditorSettingKeys
import com.rspsi.editor.settings.SettingsStore
import com.rspsi.editor.tool.DuplicateObjectTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.MoveObjectTool
import com.rspsi.editor.tool.PlaceObjectTool
import com.rspsi.editor.tool.RotateObjectTool

/**
 * Shared object-tool state projected into any frontend's controls.
 *
 * This is a direct behavioral migration of the Java implementation. Configuration captures one
 * settings snapshot before dispatching by tool ID, so each configured tool sees a coherent set of
 * values.
 */
class ObjectToolSettings(
    settings: SettingsStore?,
) {
    private val settings: SettingsStore =
        settings ?: throw NullPointerException("settings")

    fun settings(): List<EditorSetting> =
        java.util.List.of(
            EditorSetting.from(settings, EditorSettingKeys.OBJECT_ID),
            EditorSetting.from(settings, EditorSettingKeys.OBJECT_TYPE),
            EditorSetting.from(settings, EditorSettingKeys.OBJECT_ROTATION),
            EditorSetting.from(settings, EditorSettingKeys.OBJECT_QUARTER_TURNS),
            EditorSetting.from(settings, EditorSettingKeys.OBJECT_SNAP_GRID),
        )

    fun configure(
        toolId: String?,
        tool: EditorTool?,
    ) {
        val values = settings.snapshot()

        if (toolId == null) {
            // Match Java string-switch failure timing: snapshot first, then null selector failure.
            throw NullPointerException()
        }

        when (toolId) {
            "object.place" -> {
                val place = tool as PlaceObjectTool?
                place!!.setId(values.get(EditorSettingKeys.OBJECT_ID))
                place.setType(values.get(EditorSettingKeys.OBJECT_TYPE))
                place.setRotation(values.get(EditorSettingKeys.OBJECT_ROTATION))
            }

            "object.move" ->
                (tool as MoveObjectTool?).let {
                    it!!.setSnapGridSize(
                        values.get(EditorSettingKeys.OBJECT_SNAP_GRID),
                    )
                }

            "object.rotate" ->
                (tool as RotateObjectTool?).let {
                    it!!.setQuarterTurns(
                        values.get(EditorSettingKeys.OBJECT_QUARTER_TURNS),
                    )
                }

            "object.duplicate" ->
                (tool as DuplicateObjectTool?).let {
                    it!!.setSnapGridSize(
                        values.get(EditorSettingKeys.OBJECT_SNAP_GRID),
                    )
                }

            "object.delete" -> Unit

            else ->
                throw IllegalArgumentException(
                    "Unknown object tool: $toolId",
                )
        }
    }
}
