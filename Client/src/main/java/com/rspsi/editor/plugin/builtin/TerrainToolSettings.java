package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;

import java.util.List;

/** Shared terrain feature state projected into any frontend's tool controls. */
public final class TerrainToolSettings {
    private final SettingsStore settings;

    public TerrainToolSettings(SettingsStore settings) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public List<EditorSetting> settings() {
        return List.of(
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
                EditorSetting.from(settings, EditorSettingKeys.TERRAIN_RAMP_END));
    }

    /** Applies the current feature state to a newly-created terrain tool. */
    public void configure(String toolId, EditorTool tool) {
        SettingsSnapshot values = settings.snapshot();
        switch (toolId) {
            case "terrain.paint-underlay" -> ((PaintUnderlayTool) tool).setUnderlayId(
                    values.get(EditorSettingKeys.TERRAIN_UNDERLAY));
            case "terrain.paint-overlay" -> {
                PaintOverlayTool overlay = (PaintOverlayTool) tool;
                overlay.setOverlayId(values.get(EditorSettingKeys.TERRAIN_OVERLAY));
                overlay.setShape(values.get(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE));
                overlay.setRotation(values.get(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION));
            }
            case "terrain.raise", "terrain.lower" -> {
                ChangeHeightTool height = (ChangeHeightTool) tool;
                int delta = values.get(EditorSettingKeys.TERRAIN_HEIGHT_DELTA);
                height.setDelta("terrain.raise".equals(toolId) ? delta : -delta);
                height.setRadius(values.get(EditorSettingKeys.TERRAIN_HEIGHT_RADIUS));
                height.setFalloff(values.get(EditorSettingKeys.TERRAIN_HEIGHT_FALLOFF));
            }
            case "terrain.flatten" -> ((FlattenTerrainTool) tool).setTargetHeight(
                    values.get(EditorSettingKeys.TERRAIN_FLATTEN_HEIGHT));
            case "terrain.smooth" -> ((SmoothTerrainTool) tool).setStrengthPercent(
                    values.get(EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH));
            case "terrain.ramp" -> {
                RampTerrainTool ramp = (RampTerrainTool) tool;
                ramp.setStartHeight(values.get(EditorSettingKeys.TERRAIN_RAMP_START));
                ramp.setEndHeight(values.get(EditorSettingKeys.TERRAIN_RAMP_END));
            }
            case "terrain.flags" -> ((PaintFlagsTool) tool).setFlags(
                    values.get(EditorSettingKeys.TERRAIN_FLAGS));
            default -> throw new IllegalArgumentException("Unknown terrain tool: " + toolId);
        }
    }
}
