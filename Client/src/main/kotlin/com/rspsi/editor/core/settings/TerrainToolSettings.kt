package com.rspsi.editor.core.settings

import com.rspsi.editor.plugin.EditorSetting
import com.rspsi.editor.settings.EditorSettingKeys
import com.rspsi.editor.settings.SettingsStore
import com.rspsi.editor.tool.BlendTerrainTool
import com.rspsi.editor.tool.ChangeHeightTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.FlattenTerrainTool
import com.rspsi.editor.tool.PaintFlagsTool
import com.rspsi.editor.tool.PaintOverlayTool
import com.rspsi.editor.tool.PaintUnderlayTool
import com.rspsi.editor.tool.RampTerrainTool
import com.rspsi.editor.tool.SmoothTerrainTool
import com.rspsi.editor.tool.TerraceTerrainTool

/**
 * Shared terrain feature state projected into any frontend's tool controls.
 *
 * This is a direct behavioral migration of the Java implementation. Configuration captures one
 * settings snapshot before dispatch so each newly-created terrain tool receives a coherent view
 * of the current feature state.
 */
class TerrainToolSettings(
    settings: SettingsStore?,
) {
    private val settings: SettingsStore =
        settings ?: throw NullPointerException("settings")

    fun settings(): List<EditorSetting> =
        java.util.List.of(
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_UNDERLAY),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_OVERLAY),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_OVERLAY_SHAPE),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_OVERLAY_ROTATION),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_HEIGHT_DELTA),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_HEIGHT_RADIUS),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_HEIGHT_FALLOFF),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_FLATTEN_HEIGHT),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_FLAGS),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_RAMP_START),
            EditorSetting.from(settings, EditorSettingKeys.TERRAIN_RAMP_END),
        )

    /** Applies the current feature state to a newly-created terrain tool. */
    fun configure(
        toolId: String?,
        tool: EditorTool?,
    ) {
        val values = settings.snapshot()

        if (toolId == null) {
            // Java string-switch behavior captures the snapshot before failing on a null selector.
            throw NullPointerException()
        }

        when (toolId) {
            "terrain.paint-underlay" ->
                (tool as PaintUnderlayTool?)!!.setUnderlayId(
                    values.get(EditorSettingKeys.TERRAIN_UNDERLAY),
                )

            "terrain.paint-overlay" -> {
                val overlay = (tool as PaintOverlayTool?)!!
                overlay.setOverlayId(
                    values.get(EditorSettingKeys.TERRAIN_OVERLAY),
                )
                overlay.setShape(
                    values.get(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE),
                )
                overlay.setRotation(
                    values.get(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION),
                )
            }

            "terrain.raise",
            "terrain.lower",
            -> {
                val height = (tool as ChangeHeightTool?)!!
                val delta = values.get(EditorSettingKeys.TERRAIN_HEIGHT_DELTA)
                height.setDelta(
                    if (toolId == "terrain.raise") {
                        delta
                    } else {
                        -delta
                    },
                )
                height.setRadius(
                    values.get(EditorSettingKeys.TERRAIN_HEIGHT_RADIUS),
                )
                height.setFalloff(
                    values.get(EditorSettingKeys.TERRAIN_HEIGHT_FALLOFF),
                )
            }

            "terrain.flatten" ->
                (tool as FlattenTerrainTool?)!!.setTargetHeight(
                    values.get(EditorSettingKeys.TERRAIN_FLATTEN_HEIGHT),
                )

            "terrain.smooth" ->
                (tool as SmoothTerrainTool?)!!.setStrengthPercent(
                    values.get(EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH),
                )

            "terrain.blend" -> {
                val blend = (tool as BlendTerrainTool?)!!
                blend.setStrengthPercent(
                    values.get(EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH),
                )
                blend.setEdgeThreshold(56)
            }

            "terrain.terrace" ->
                (tool as TerraceTerrainTool?)!!.setStep(16)

            "terrain.ramp" -> {
                val ramp = (tool as RampTerrainTool?)!!
                ramp.setStartHeight(
                    values.get(EditorSettingKeys.TERRAIN_RAMP_START),
                )
                ramp.setEndHeight(
                    values.get(EditorSettingKeys.TERRAIN_RAMP_END),
                )
            }

            "terrain.flags" ->
                (tool as PaintFlagsTool?)!!.setFlags(
                    values.get(EditorSettingKeys.TERRAIN_FLAGS),
                )

            else ->
                throw IllegalArgumentException(
                    "Unknown terrain tool: $toolId",
                )
        }
    }
}
