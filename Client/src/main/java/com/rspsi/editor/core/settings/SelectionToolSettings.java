package com.rspsi.editor.core.settings;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;

import java.util.List;

/** Shared selection/transform state projected into any frontend's controls. */
public final class SelectionToolSettings {
    private final SettingsStore settings;

    public SelectionToolSettings(SettingsStore settings) {
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public List<EditorSetting> settings() {
        return List.of(
                EditorSetting.from(settings, EditorSettingKeys.SELECTION_QUARTER_TURNS),
                EditorSetting.from(settings, EditorSettingKeys.SELECTION_SNAP_GRID),
                EditorSetting.from(settings, EditorSettingKeys.SELECTION_REPLACEMENT_ID));
    }

    public void configure(String toolId, EditorTool tool) {
        SettingsSnapshot values = settings.snapshot();
        switch (toolId) {
            case "selection.move" -> ((MoveSelectionTool) tool).setSnapGridSize(
                    values.get(EditorSettingKeys.SELECTION_SNAP_GRID));
            case "selection.rotate" -> ((RotateSelectionTool) tool).setQuarterTurns(
                    values.get(EditorSettingKeys.SELECTION_QUARTER_TURNS));
            case "selection.duplicate" -> ((DuplicateSelectionTool) tool).setSnapGridSize(
                    values.get(EditorSettingKeys.SELECTION_SNAP_GRID));
            case "selection.replace" -> ((ReplaceSelectionTool) tool).setReplacementId(
                    values.get(EditorSettingKeys.SELECTION_REPLACEMENT_ID));
            case "selection.box", "selection.lasso", "selection.attribute" -> { }
            default -> throw new IllegalArgumentException("Unknown selection tool: " + toolId);
        }
    }
}
