package com.rspsi.editor.core.settings;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectToolSettingsCompatibilityTest {

    @Test
    void classKeepsPublicFinalJavaSurface() throws Exception {
        int modifiers = ObjectToolSettings.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));

        assertTrue(Modifier.isPublic(
                ObjectToolSettings.class
                        .getDeclaredConstructor(SettingsStore.class)
                        .getModifiers()));

        assertEquals(
                List.class,
                ObjectToolSettings.class
                        .getDeclaredMethod("settings")
                        .getReturnType());

        var configure = ObjectToolSettings.class.getDeclaredMethod(
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
                () -> new ObjectToolSettings(null));
        assertEquals("settings", failure.getMessage());
    }

    @Test
    void settingsProjectionPreservesExactOrderAndImmutability() {
        ObjectToolSettings settings = settings();

        List<EditorSetting> projected = settings.settings();

        assertEquals(5, projected.size());
        assertEquals(
                List.of(
                        "objects.id",
                        "objects.type",
                        "objects.rotation",
                        "objects.quarter-turns",
                        "objects.snap-grid"),
                projected.stream().map(EditorSetting::id).toList());

        assertThrows(
                UnsupportedOperationException.class,
                () -> projected.add(projected.get(0)));
    }

    @Test
    void configureAppliesCurrentSnapshotValuesToObjectTools() {
        SettingsStore store = store();
        ObjectToolSettings settings = new ObjectToolSettings(store);

        store.set(EditorSettingKeys.OBJECT_ID, 321);
        store.set(EditorSettingKeys.OBJECT_TYPE, 22);
        store.set(EditorSettingKeys.OBJECT_ROTATION, 3);
        store.set(EditorSettingKeys.OBJECT_QUARTER_TURNS, 2);
        store.set(EditorSettingKeys.OBJECT_SNAP_GRID, 8);

        PlaceObjectTool place = new PlaceObjectTool(0, 10, 0);
        settings.configure("object.place", place);
        assertEquals(321, place.idValue());
        assertEquals(22, place.type());
        assertEquals(3, place.rotation());

        MoveObjectTool move = new MoveObjectTool();
        settings.configure("object.move", move);
        assertEquals(8, move.snapGridSize());

        RotateObjectTool rotate = new RotateObjectTool();
        settings.configure("object.rotate", rotate);
        assertEquals(2, rotate.quarterTurns());

        DuplicateObjectTool duplicate = new DuplicateObjectTool();
        settings.configure("object.duplicate", duplicate);
        assertEquals(8, duplicate.snapGridSize());
    }

    @Test
    void deleteIdRemainsPassiveAndDoesNotRequireToolInstance() {
        ObjectToolSettings settings = settings();

        assertDoesNotThrow(() -> settings.configure("object.delete", null));
        assertDoesNotThrow(() ->
                settings.configure("object.delete", new DeleteObjectTool()));
    }

    @Test
    void unknownAndNullToolIdsPreserveFailureKinds() {
        ObjectToolSettings settings = settings();

        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> settings.configure("object.nope", null));
        assertEquals("Unknown object tool: object.nope", unknown.getMessage());

        assertThrows(
                NullPointerException.class,
                () -> settings.configure(null, null));
    }

    @Test
    void activeToolIdsStillRequireExpectedConcreteToolType() {
        ObjectToolSettings settings = settings();

        assertThrows(
                ClassCastException.class,
                () -> settings.configure(
                        "object.move",
                        new RotateObjectTool()));

        assertThrows(
                NullPointerException.class,
                () -> settings.configure("object.place", null));
    }

    private static ObjectToolSettings settings() {
        return new ObjectToolSettings(store());
    }

    private static SettingsStore store() {
        return new SettingsStore(EditorSettingKeys.registry());
    }
}
