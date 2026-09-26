package com.rspsi.editor.core.settings;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.BlendTerrainTool;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import com.rspsi.editor.tool.TerraceTerrainTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainToolSettingsCompatibilityTest {

    @Test
    void classKeepsPublicFinalJavaSurface() throws Exception {
        int modifiers = TerrainToolSettings.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));

        assertTrue(Modifier.isPublic(
                TerrainToolSettings.class
                        .getDeclaredConstructor(SettingsStore.class)
                        .getModifiers()));

        assertEquals(
                List.class,
                TerrainToolSettings.class
                        .getDeclaredMethod("settings")
                        .getReturnType());

        var configure = TerrainToolSettings.class.getDeclaredMethod(
                "configure",
                String.class,
                EditorTool.class);
        assertTrue(Modifier.isPublic(configure.getModifiers()));
        assertEquals(void.class, configure.getReturnType());
    }

    @Test
    void constructorPreservesNullFailureMessage() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new TerrainToolSettings(null));
        assertEquals("settings", failure.getMessage());
    }

    @Test
    void settingsProjectionPreservesExactOrderAndImmutability() {
        TerrainToolSettings settings = settings();

        List<EditorSetting> projected = settings.settings();

        assertEquals(12, projected.size());
        assertEquals(
                List.of(
                        "terrain.underlay",
                        "terrain.overlay",
                        "terrain.overlay-shape",
                        "terrain.overlay-rotation",
                        "terrain.height-delta",
                        "terrain.height-radius",
                        "terrain.height-falloff",
                        "terrain.flatten-height",
                        "terrain.smooth-strength",
                        "terrain.flags",
                        "terrain.ramp-start",
                        "terrain.ramp-end"),
                projected.stream().map(EditorSetting::id).toList());

        assertThrows(
                UnsupportedOperationException.class,
                () -> projected.add(projected.get(0)));
    }

    @Test
    void configureAppliesCurrentSnapshotValuesToTerrainTools() {
        SettingsStore store = store();
        TerrainToolSettings settings = new TerrainToolSettings(store);

        store.set(EditorSettingKeys.TERRAIN_UNDERLAY, 42);
        store.set(EditorSettingKeys.TERRAIN_OVERLAY, 43);
        store.set(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE, 7);
        store.set(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION, 2);
        store.set(EditorSettingKeys.TERRAIN_HEIGHT_DELTA, 12);
        store.set(EditorSettingKeys.TERRAIN_HEIGHT_RADIUS, 4);
        store.set(EditorSettingKeys.TERRAIN_HEIGHT_FALLOFF, ChangeHeightTool.Falloff.SMOOTH);
        store.set(EditorSettingKeys.TERRAIN_FLATTEN_HEIGHT, -128);
        store.set(EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH, 75);
        store.set(EditorSettingKeys.TERRAIN_FLAGS, 123);
        store.set(EditorSettingKeys.TERRAIN_RAMP_START, -50);
        store.set(EditorSettingKeys.TERRAIN_RAMP_END, 90);

        PaintUnderlayTool underlay = new PaintUnderlayTool(1);
        settings.configure("terrain.paint-underlay", underlay);
        assertEquals(42, underlay.underlayId());

        PaintOverlayTool overlay = new PaintOverlayTool(1);
        settings.configure("terrain.paint-overlay", overlay);
        assertEquals(43, overlay.overlayId());
        assertEquals(7, overlay.shape());
        assertEquals(2, overlay.rotation());

        ChangeHeightTool raise = new ChangeHeightTool(1);
        settings.configure("terrain.raise", raise);
        assertEquals(12, raise.delta());
        assertEquals(4, raise.radius());
        assertEquals(ChangeHeightTool.Falloff.SMOOTH, raise.falloff());

        ChangeHeightTool lower = new ChangeHeightTool(-1);
        settings.configure("terrain.lower", lower);
        assertEquals(-12, lower.delta());
        assertEquals(4, lower.radius());
        assertEquals(ChangeHeightTool.Falloff.SMOOTH, lower.falloff());

        FlattenTerrainTool flatten = new FlattenTerrainTool(0);
        settings.configure("terrain.flatten", flatten);
        assertEquals(-128, flatten.targetHeight());

        SmoothTerrainTool smooth = new SmoothTerrainTool(50);
        settings.configure("terrain.smooth", smooth);
        assertEquals(75, smooth.strengthPercent());

        BlendTerrainTool blend = new BlendTerrainTool(50, 1);
        settings.configure("terrain.blend", blend);
        assertEquals(75, blend.strengthPercent());
        assertEquals(56, blend.edgeThreshold());

        TerraceTerrainTool terrace = new TerraceTerrainTool(2);
        settings.configure("terrain.terrace", terrace);
        assertEquals(16, terrace.step());

        RampTerrainTool ramp = new RampTerrainTool(0, 64);
        settings.configure("terrain.ramp", ramp);
        assertEquals(-50, ramp.startHeight());
        assertEquals(90, ramp.endHeight());

        PaintFlagsTool flags = new PaintFlagsTool(0);
        settings.configure("terrain.flags", flags);
        assertEquals(123, flags.flags());
    }

    @Test
    void unknownAndNullToolIdsPreserveFailureKinds() {
        TerrainToolSettings settings = settings();

        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> settings.configure("terrain.nope", null));
        assertEquals("Unknown terrain tool: terrain.nope", unknown.getMessage());

        assertThrows(
                NullPointerException.class,
                () -> settings.configure(null, null));
    }

    @Test
    void activeToolIdsStillRequireExpectedConcreteToolType() {
        TerrainToolSettings settings = settings();

        assertThrows(
                ClassCastException.class,
                () -> settings.configure(
                        "terrain.paint-underlay",
                        new PaintOverlayTool(1)));

        assertThrows(
                NullPointerException.class,
                () -> settings.configure("terrain.raise", null));
    }

    private static TerrainToolSettings settings() {
        return new TerrainToolSettings(store());
    }

    private static SettingsStore store() {
        return new SettingsStore(EditorSettingKeys.registry());
    }
}
