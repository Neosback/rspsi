package com.rspsi.editor.core.settings;

import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelectionToolSettingsCompatibilityTest {

    @Test
    void classKeepsPublicFinalJavaSurface() throws Exception {
        int modifiers = SelectionToolSettings.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));

        assertTrue(Modifier.isPublic(
                SelectionToolSettings.class
                        .getDeclaredConstructor(SettingsStore.class)
                        .getModifiers()));

        assertEquals(
                List.class,
                SelectionToolSettings.class
                        .getDeclaredMethod("settings")
                        .getReturnType());

        var configure = SelectionToolSettings.class.getDeclaredMethod(
                "configure",
                String.class,
                EditorTool.class);
        assertEquals(void.class, configure.getReturnType());
        assertTrue(Modifier.isPublic(configure.getModifiers()));
    }

    @Test
    void constructorPreservesNullFailureMessage() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new SelectionToolSettings(null));
        assertEquals("settings", failure.getMessage());
    }

    @Test
    void settingsProjectionPreservesExactOrder() {
        SelectionToolSettings settings = settings();

        List<EditorSetting> projected = settings.settings();

        assertEquals(3, projected.size());
        assertEquals(
                List.of(
                        "selection.quarter-turns",
                        "selection.snap-grid",
                        "selection.replacement-id"),
                projected.stream().map(EditorSetting::id).toList());

        assertThrows(
                UnsupportedOperationException.class,
                () -> projected.add(projected.get(0)));
    }

    @Test
    void configureAppliesSnapshotValuesToTransformTools() {
        SettingsStore store = store();
        store.set(EditorSettingKeys.SELECTION_QUARTER_TURNS, 3);
        store.set(EditorSettingKeys.SELECTION_SNAP_GRID, 8);
        store.set(EditorSettingKeys.SELECTION_REPLACEMENT_ID, 1234);

        SelectionToolSettings settings = new SelectionToolSettings(store);

        MoveSelectionTool move = new MoveSelectionTool();
        settings.configure("selection.move", move);
        assertEquals(8, move.snapGridSize());

        RotateSelectionTool rotate = new RotateSelectionTool();
        settings.configure("selection.rotate", rotate);
        assertEquals(3, rotate.quarterTurns());

        DuplicateSelectionTool duplicate = new DuplicateSelectionTool();
        settings.configure("selection.duplicate", duplicate);
        assertEquals(8, duplicate.snapGridSize());

        ReplaceSelectionTool replace = new ReplaceSelectionTool(0);
        settings.configure("selection.replace", replace);
        assertEquals(1234, replace.replacementId());
    }

    @Test
    void passiveSelectionIdsRemainNoOpsAndDoNotRequireToolInstance() {
        SelectionToolSettings settings = settings();

        assertDoesNotThrow(() -> settings.configure("selection.box", null));
        assertDoesNotThrow(() -> settings.configure("selection.lasso", null));
        assertDoesNotThrow(() -> settings.configure("selection.attribute", null));
    }

    @Test
    void unknownAndNullToolIdsPreserveFailureKinds() {
        SelectionToolSettings settings = settings();

        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> settings.configure("selection.nope", null));
        assertEquals("Unknown selection tool: selection.nope", unknown.getMessage());

        assertThrows(
                NullPointerException.class,
                () -> settings.configure(null, null));
    }

    @Test
    void activeToolIdsStillRequireExpectedConcreteToolType() {
        SelectionToolSettings settings = settings();

        assertThrows(
                ClassCastException.class,
                () -> settings.configure(
                        "selection.move",
                        new RotateSelectionTool()));

        assertThrows(
                NullPointerException.class,
                () -> settings.configure("selection.move", null));
    }

    private static SelectionToolSettings settings() {
        return new SelectionToolSettings(store());
    }

    private static SettingsStore store() {
        return new SettingsStore(EditorSettingKeys.registry());
    }
}
