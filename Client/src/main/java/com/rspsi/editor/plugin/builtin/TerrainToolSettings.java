package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorSetting;
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
    private int underlayId = 1;
    private int overlayId = 1;
    private int overlayShape;
    private int overlayRotation;
    private int heightDelta = 8;
    private int heightRadius;
    private ChangeHeightTool.Falloff heightFalloff = ChangeHeightTool.Falloff.NONE;
    private int flattenHeight;
    private int smoothStrength = 50;
    private int flags;
    private int rampStart;
    private int rampEnd = 64;

    public List<EditorSetting> settings() {
        return List.of(
                EditorSetting.integer("terrain.underlay", "Underlay", 0, 65535,
                        () -> underlayId, this::setUnderlayId),
                EditorSetting.integer("terrain.overlay", "Overlay", 0, 65535,
                        () -> overlayId, this::setOverlayId),
                EditorSetting.integer("terrain.overlay-shape", "Overlay shape", 0, 11,
                        () -> overlayShape, this::setOverlayShape),
                EditorSetting.integer("terrain.overlay-rotation", "Overlay rotation", 0, 3,
                        () -> overlayRotation, this::setOverlayRotation),
                EditorSetting.integer("terrain.height-delta", "Height delta", 0, 1024,
                        () -> heightDelta, this::setHeightDelta),
                EditorSetting.integer("terrain.height-radius", "Height radius", 0, 64,
                        () -> heightRadius, this::setHeightRadius),
                EditorSetting.enumeration("terrain.height-falloff", "Height falloff",
                        List.of("NONE", "LINEAR", "SMOOTH"),
                        () -> heightFalloff.name(), value -> heightFalloff = ChangeHeightTool.Falloff.valueOf(value)),
                EditorSetting.integer("terrain.flatten-height", "Flatten", Integer.MIN_VALUE,
                        Integer.MAX_VALUE, () -> flattenHeight, this::setFlattenHeight),
                EditorSetting.integer("terrain.smooth-strength", "Smooth strength", 0, 100,
                        () -> smoothStrength, this::setSmoothStrength),
                EditorSetting.integer("terrain.flags", "Flags", 0, Integer.MAX_VALUE,
                        () -> flags, this::setFlags),
                EditorSetting.integer("terrain.ramp-start", "Ramp start", Integer.MIN_VALUE,
                        Integer.MAX_VALUE, () -> rampStart, this::setRampStart),
                EditorSetting.integer("terrain.ramp-end", "Ramp end", Integer.MIN_VALUE,
                        Integer.MAX_VALUE, () -> rampEnd, this::setRampEnd));
    }

    /** Applies the current feature state to a newly-created terrain tool. */
    public void configure(String toolId, EditorTool tool) {
        switch (toolId) {
            case "terrain.paint-underlay" -> ((PaintUnderlayTool) tool).setUnderlayId(underlayId);
            case "terrain.paint-overlay" -> {
                PaintOverlayTool overlay = (PaintOverlayTool) tool;
                overlay.setOverlayId(overlayId);
                overlay.setShape(overlayShape);
                overlay.setRotation(overlayRotation);
            }
            case "terrain.raise", "terrain.lower" -> {
                ChangeHeightTool height = (ChangeHeightTool) tool;
                height.setDelta("terrain.raise".equals(toolId) ? heightDelta : -heightDelta);
                height.setRadius(heightRadius);
                height.setFalloff(heightFalloff);
            }
            case "terrain.flatten" -> ((FlattenTerrainTool) tool).setTargetHeight(flattenHeight);
            case "terrain.smooth" -> ((SmoothTerrainTool) tool).setStrengthPercent(smoothStrength);
            case "terrain.ramp" -> {
                RampTerrainTool ramp = (RampTerrainTool) tool;
                ramp.setStartHeight(rampStart);
                ramp.setEndHeight(rampEnd);
            }
            case "terrain.flags" -> ((PaintFlagsTool) tool).setFlags(flags);
            default -> throw new IllegalArgumentException("Unknown terrain tool: " + toolId);
        }
    }

    private void setUnderlayId(int value) { underlayId = value; }
    private void setOverlayId(int value) { overlayId = value; }
    private void setOverlayShape(int value) { overlayShape = value; }
    private void setOverlayRotation(int value) { overlayRotation = value; }
    private void setHeightDelta(int value) { heightDelta = value; }
    private void setHeightRadius(int value) { heightRadius = value; }
    private void setFlattenHeight(int value) { flattenHeight = value; }
    private void setSmoothStrength(int value) { smoothStrength = value; }
    private void setFlags(int value) { flags = value; }
    private void setRampStart(int value) { rampStart = value; }
    private void setRampEnd(int value) { rampEnd = value; }
}
