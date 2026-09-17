package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;

import java.util.List;

/** Shared selection/transform state projected into any frontend's controls. */
public final class SelectionToolSettings {
    private int quarterTurns = 1;
    private int snapGridSize = 1;
    private int replacementId;

    public List<EditorSetting> settings() {
        return List.of(
                EditorSetting.integer("selection.quarter-turns", "Selection quarter turns", 1, 3,
                        () -> quarterTurns, this::setQuarterTurns),
                EditorSetting.integer("selection.snap-grid", "Snap grid", 1, 64,
                        () -> snapGridSize, this::setSnapGridSize),
                EditorSetting.integer("selection.replacement-id", "Replacement ID", 0,
                        Integer.MAX_VALUE, () -> replacementId, this::setReplacementId));
    }

    public void configure(String toolId, EditorTool tool) {
        switch (toolId) {
            case "selection.move" -> ((MoveSelectionTool) tool).setSnapGridSize(snapGridSize);
            case "selection.rotate" -> ((RotateSelectionTool) tool).setQuarterTurns(quarterTurns);
            case "selection.duplicate" -> ((DuplicateSelectionTool) tool).setSnapGridSize(snapGridSize);
            case "selection.replace" -> ((ReplaceSelectionTool) tool).setReplacementId(replacementId);
            case "selection.box", "selection.lasso", "selection.attribute" -> { }
            default -> throw new IllegalArgumentException("Unknown selection tool: " + toolId);
        }
    }

    private void setQuarterTurns(int value) { quarterTurns = value; }
    private void setSnapGridSize(int value) { snapGridSize = value; }
    private void setReplacementId(int value) { replacementId = value; }
}
