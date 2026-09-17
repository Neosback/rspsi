package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;

import java.util.List;

/** Shared object-tool state projected into any frontend's controls. */
public final class ObjectToolSettings {
    private int objectId;
    private int objectType = 10;
    private int objectRotation;
    private int quarterTurns = 1;
    private int snapGridSize = 1;

    public List<EditorSetting> settings() {
        return List.of(
                EditorSetting.integer("objects.id", "Object ID", 0, Integer.MAX_VALUE,
                        () -> objectId, this::setObjectId),
                EditorSetting.integer("objects.type", "Object type", 0, 22,
                        () -> objectType, this::setObjectType),
                EditorSetting.integer("objects.rotation", "Object rotation", 0, 3,
                        () -> objectRotation, this::setObjectRotation),
                EditorSetting.integer("objects.quarter-turns", "Object quarter turns", 1, 3,
                        () -> quarterTurns, this::setQuarterTurns),
                EditorSetting.integer("objects.snap-grid", "Snap grid", 1, 64,
                        () -> snapGridSize, this::setSnapGridSize));
    }

    public void configure(String toolId, EditorTool tool) {
        switch (toolId) {
            case "object.place" -> {
                PlaceObjectTool place = (PlaceObjectTool) tool;
                place.setId(objectId);
                place.setType(objectType);
                place.setRotation(objectRotation);
            }
            case "object.move" -> ((MoveObjectTool) tool).setSnapGridSize(snapGridSize);
            case "object.rotate" -> ((RotateObjectTool) tool).setQuarterTurns(quarterTurns);
            case "object.duplicate" -> ((DuplicateObjectTool) tool).setSnapGridSize(snapGridSize);
            case "object.delete" -> { }
            default -> throw new IllegalArgumentException("Unknown object tool: " + toolId);
        }
    }

    private void setObjectId(int value) { objectId = value; }
    private void setObjectType(int value) { objectType = value; }
    private void setObjectRotation(int value) { objectRotation = value; }
    private void setQuarterTurns(int value) { quarterTurns = value; }
    private void setSnapGridSize(int value) { snapGridSize = value; }
}
