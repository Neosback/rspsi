package com.rspsi.editor.core.settings;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;

import java.util.List;

/** Shared object-tool state projected into any frontend's controls. */
public final class ObjectToolSettings {
    private final SettingsStore settings;

    public ObjectToolSettings(SettingsStore settings) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public List<EditorSetting> settings() {
        return List.of(
                EditorSetting.from(settings, EditorSettingKeys.OBJECT_ID),
                EditorSetting.from(settings, EditorSettingKeys.OBJECT_TYPE),
                EditorSetting.from(settings, EditorSettingKeys.OBJECT_ROTATION),
                EditorSetting.from(settings, EditorSettingKeys.OBJECT_QUARTER_TURNS),
                EditorSetting.from(settings, EditorSettingKeys.OBJECT_SNAP_GRID));
    }

    public void configure(String toolId, EditorTool tool) {
        SettingsSnapshot values = settings.snapshot();
        switch (toolId) {
            case "object.place" -> {
                PlaceObjectTool place = (PlaceObjectTool) tool;
                place.setId(values.get(EditorSettingKeys.OBJECT_ID));
                place.setType(values.get(EditorSettingKeys.OBJECT_TYPE));
                place.setRotation(values.get(EditorSettingKeys.OBJECT_ROTATION));
            }
            case "object.move" -> ((MoveObjectTool) tool).setSnapGridSize(
                    values.get(EditorSettingKeys.OBJECT_SNAP_GRID));
            case "object.rotate" -> ((RotateObjectTool) tool).setQuarterTurns(
                    values.get(EditorSettingKeys.OBJECT_QUARTER_TURNS));
            case "object.duplicate" -> ((DuplicateObjectTool) tool).setSnapGridSize(
                    values.get(EditorSettingKeys.OBJECT_SNAP_GRID));
            case "object.delete" -> { }
            default -> throw new IllegalArgumentException("Unknown object tool: " + toolId);
        }
    }
}
