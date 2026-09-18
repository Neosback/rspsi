package com.rspsi.editor.settings;

import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.render.RenderSettingKeys;

import java.util.List;
import java.util.Set;

/** Canonical setting registry for the editor core and first-party tools. */
public final class EditorSettingKeys {
    private EditorSettingKeys() {
    }

    public static final SettingKey<Integer> TERRAIN_UNDERLAY = integer("terrain.underlay");
    public static final SettingKey<Integer> TERRAIN_OVERLAY = integer("terrain.overlay");
    public static final SettingKey<Integer> TERRAIN_OVERLAY_SHAPE = integer("terrain.overlay-shape");
    public static final SettingKey<Integer> TERRAIN_OVERLAY_ROTATION = integer("terrain.overlay-rotation");
    public static final SettingKey<Integer> TERRAIN_HEIGHT_DELTA = integer("terrain.height-delta");
    public static final SettingKey<Integer> TERRAIN_HEIGHT_RADIUS = integer("terrain.height-radius");
    public static final SettingKey<ChangeHeightTool.Falloff> TERRAIN_HEIGHT_FALLOFF =
            new SettingKey<>("terrain.height-falloff", ChangeHeightTool.Falloff.class);
    public static final SettingKey<Integer> TERRAIN_FLATTEN_HEIGHT = integer("terrain.flatten-height");
    public static final SettingKey<Integer> TERRAIN_SMOOTH_STRENGTH = integer("terrain.smooth-strength");
    public static final SettingKey<Integer> TERRAIN_FLAGS = integer("terrain.flags");
    public static final SettingKey<Integer> TERRAIN_RAMP_START = integer("terrain.ramp-start");
    public static final SettingKey<Integer> TERRAIN_RAMP_END = integer("terrain.ramp-end");

    public static final SettingKey<Integer> OBJECT_ID = integer("objects.id");
    public static final SettingKey<Integer> OBJECT_TYPE = integer("objects.type");
    public static final SettingKey<Integer> OBJECT_ROTATION = integer("objects.rotation");
    public static final SettingKey<Integer> OBJECT_QUARTER_TURNS = integer("objects.quarter-turns");
    public static final SettingKey<Integer> OBJECT_SNAP_GRID = integer("objects.snap-grid");

    public static final SettingKey<Integer> SELECTION_QUARTER_TURNS = integer("selection.quarter-turns");
    public static final SettingKey<Integer> SELECTION_SNAP_GRID = integer("selection.snap-grid");
    public static final SettingKey<Integer> SELECTION_REPLACEMENT_ID = integer("selection.replacement-id");

    public static SettingsRegistry registry() {
        SettingsRegistry registry = RenderSettingKeys.registry();
        Set<SettingInvalidation> redraw = Set.of(SettingInvalidation.REDRAW);
        registry.register(SettingSpec.integer(TERRAIN_UNDERLAY, 1, 0, 65535,
                SettingScope.VIEWPORT, "Underlay", "Active terrain underlay ID.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_OVERLAY, 1, 0, 65535,
                SettingScope.VIEWPORT, "Overlay", "Active terrain overlay ID.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_OVERLAY_SHAPE, 0, 0, 11,
                SettingScope.VIEWPORT, "Overlay shape", "Active terrain overlay shape.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_OVERLAY_ROTATION, 0, 0, 3,
                SettingScope.VIEWPORT, "Overlay rotation", "Active terrain overlay rotation.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_HEIGHT_DELTA, 8, 0, 1024,
                SettingScope.VIEWPORT, "Height delta", "Terrain raise/lower amount.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_HEIGHT_RADIUS, 0, 0, 64,
                SettingScope.VIEWPORT, "Height radius", "Terrain brush radius.", redraw));
        registry.register(SettingSpec.enumeration(TERRAIN_HEIGHT_FALLOFF,
                ChangeHeightTool.Falloff.NONE, List.of(ChangeHeightTool.Falloff.values()),
                SettingScope.VIEWPORT, "Height falloff", "Terrain brush falloff.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_FLATTEN_HEIGHT, 0, Integer.MIN_VALUE,
                Integer.MAX_VALUE, SettingScope.VIEWPORT, "Flatten", "Target terrain height.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_SMOOTH_STRENGTH, 50, 0, 100,
                SettingScope.VIEWPORT, "Smooth strength", "Terrain smoothing strength.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_FLAGS, 0, 0, Integer.MAX_VALUE,
                SettingScope.VIEWPORT, "Flags", "Terrain flags bit mask.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_RAMP_START, 0, Integer.MIN_VALUE,
                Integer.MAX_VALUE, SettingScope.VIEWPORT, "Ramp start", "Ramp start height.", redraw));
        registry.register(SettingSpec.integer(TERRAIN_RAMP_END, 64, Integer.MIN_VALUE,
                Integer.MAX_VALUE, SettingScope.VIEWPORT, "Ramp end", "Ramp end height.", redraw));

        registry.register(SettingSpec.integer(OBJECT_ID, 0, 0, Integer.MAX_VALUE,
                SettingScope.VIEWPORT, "Object ID", "Object definition to place.", redraw));
        registry.register(SettingSpec.integer(OBJECT_TYPE, 10, 0, 22,
                SettingScope.VIEWPORT, "Object type", "Object placement type.", redraw));
        registry.register(SettingSpec.integer(OBJECT_ROTATION, 0, 0, 3,
                SettingScope.VIEWPORT, "Object rotation", "Object placement rotation.", redraw));
        registry.register(SettingSpec.integer(OBJECT_QUARTER_TURNS, 1, 1, 3,
                SettingScope.VIEWPORT, "Object quarter turns", "Object transform rotation steps.", redraw));
        registry.register(SettingSpec.integer(OBJECT_SNAP_GRID, 1, 1, 64,
                SettingScope.VIEWPORT, "Object snap grid", "Object movement snap size.", redraw));

        registry.register(SettingSpec.integer(SELECTION_QUARTER_TURNS, 1, 1, 3,
                SettingScope.VIEWPORT, "Selection quarter turns", "Selection transform rotation steps.", redraw));
        registry.register(SettingSpec.integer(SELECTION_SNAP_GRID, 1, 1, 64,
                SettingScope.VIEWPORT, "Selection snap grid", "Selection movement snap size.", redraw));
        registry.register(SettingSpec.integer(SELECTION_REPLACEMENT_ID, 0, 0, Integer.MAX_VALUE,
                SettingScope.VIEWPORT, "Replacement ID", "Object definition used by replacement tools.", redraw));
        return registry;
    }

    public static SettingConsumerCatalog consumerCatalog() {
        SettingConsumerCatalog consumers = RenderSettingKeys.consumerCatalog();
        consumers.register("terrain-tools", TERRAIN_UNDERLAY, TERRAIN_OVERLAY,
                TERRAIN_OVERLAY_SHAPE, TERRAIN_OVERLAY_ROTATION, TERRAIN_HEIGHT_DELTA,
                TERRAIN_HEIGHT_RADIUS, TERRAIN_HEIGHT_FALLOFF, TERRAIN_FLATTEN_HEIGHT,
                TERRAIN_SMOOTH_STRENGTH, TERRAIN_FLAGS, TERRAIN_RAMP_START, TERRAIN_RAMP_END);
        consumers.register("object-tools", OBJECT_ID, OBJECT_TYPE, OBJECT_ROTATION,
                OBJECT_QUARTER_TURNS, OBJECT_SNAP_GRID);
        consumers.register("selection-tools", SELECTION_QUARTER_TURNS, SELECTION_SNAP_GRID,
                SELECTION_REPLACEMENT_ID);
        return consumers;
    }

    private static SettingKey<Integer> integer(String id) {
        return new SettingKey<>(id, Integer.class);
    }
}
