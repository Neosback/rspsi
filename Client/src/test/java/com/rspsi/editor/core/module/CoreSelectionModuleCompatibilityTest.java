package com.rspsi.editor.core.module;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.tool.AttributeSelectionTool;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.LassoSelectTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreSelectionModuleCompatibilityTest {

    @Test
    void moduleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        int modifiers = CoreSelectionModule.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertTrue(Modifier.isPublic(
                CoreSelectionModule.class.getDeclaredConstructor().getModifiers()));

        var idField = CoreSelectionModule.class.getDeclaredField("ID");
        int idModifiers = idField.getModifiers();
        assertTrue(Modifier.isPublic(idModifiers));
        assertTrue(Modifier.isStatic(idModifiers));
        assertTrue(Modifier.isFinal(idModifiers));
        assertEquals("rspsi.tools.selection", idField.get(null));

        CoreSelectionModule module = new CoreSelectionModule();
        assertEquals("rspsi.tools.selection", module.id());
        assertEquals(30, module.order());
        assertEquals("0.1.0", module.version());
    }

    @Test
    void installRegistersExactSelectionToolInventoryAndMetadata() {
        Fixture fixture = fixture();
        new CoreSelectionModule().install(fixture.context());

        List<EditorToolRegistration> tools = fixture.registry().toolRegistrations();
        assertEquals(7, tools.size());

        assertTool(tools.get(0),
                "selection.box", "Box select", "Selection", "Selector",
                "SELECT_BOX", "1", 10);
        assertTool(tools.get(1),
                "selection.lasso", "Lasso select", "Selection", "Selector",
                "SELECT_LASSO", "Shift+1", 20);
        assertTool(tools.get(2),
                "selection.attribute", "Select by attribute", "Selection", "Selector",
                "SELECT_ATTR", null, 30);
        assertTool(tools.get(3),
                "selection.move", "Move selection", "Selection", "Selector",
                "MOVE", "W", 40);
        assertTool(tools.get(4),
                "selection.rotate", "Rotate selection", "Selection", "Selector",
                "ROTATE", "R", 50);
        assertTool(tools.get(5),
                "selection.duplicate", "Duplicate selection", "Selection", "Selector",
                "DUPLICATE", "Shift+D", 60);
        assertTool(tools.get(6),
                "selection.replace", "Replace selection", "Selection", "Selector",
                "REPLACE", null, 70);
    }

    @Test
    void factoriesCreateExpectedToolsAndApplyCurrentSettingsLazily() {
        Fixture fixture = fixture();
        new CoreSelectionModule().install(fixture.context());

        fixture.context().settings().set(EditorSettingKeys.SELECTION_SNAP_GRID, 8);
        fixture.context().settings().set(EditorSettingKeys.SELECTION_QUARTER_TURNS, 3);
        fixture.context().settings().set(EditorSettingKeys.SELECTION_REPLACEMENT_ID, 1234);

        assertInstanceOf(
                BoxSelectTool.class,
                fixture.registry().createTool("selection.box"));
        assertInstanceOf(
                LassoSelectTool.class,
                fixture.registry().createTool("selection.lasso"));
        assertInstanceOf(
                AttributeSelectionTool.class,
                fixture.registry().createTool("selection.attribute"));

        MoveSelectionTool move = assertInstanceOf(
                MoveSelectionTool.class,
                fixture.registry().createTool("selection.move"));
        assertEquals(8, move.snapGridSize());

        RotateSelectionTool rotate = assertInstanceOf(
                RotateSelectionTool.class,
                fixture.registry().createTool("selection.rotate"));
        assertEquals(3, rotate.quarterTurns());

        DuplicateSelectionTool duplicate = assertInstanceOf(
                DuplicateSelectionTool.class,
                fixture.registry().createTool("selection.duplicate"));
        assertEquals(8, duplicate.snapGridSize());

        ReplaceSelectionTool replace = assertInstanceOf(
                ReplaceSelectionTool.class,
                fixture.registry().createTool("selection.replace"));
        assertEquals(1234, replace.replacementId());
    }

    @Test
    void installRegistersExactSelectionSettingsContext() {
        Fixture fixture = fixture();
        new CoreSelectionModule().install(fixture.context());

        List<EditorToolContextRegistration> contexts =
                fixture.registry().toolContextRegistrations();
        assertEquals(1, contexts.size());

        EditorToolContextRegistration context = contexts.get(0);
        assertEquals("selection.context", context.id());
        assertEquals("Selection settings", context.label());
        assertEquals(0, context.order());
        assertEquals(
                List.of(
                        "selection.box",
                        "selection.lasso",
                        "selection.attribute",
                        "selection.move",
                        "selection.rotate",
                        "selection.duplicate",
                        "selection.replace"),
                context.toolIds());

        assertEquals(
                List.of(
                        "selection.quarter-turns",
                        "selection.snap-grid",
                        "selection.replacement-id"),
                fixture.registry()
                        .settingsForTool(fixture.context(), "selection.move")
                        .stream()
                        .map(com.rspsi.editor.plugin.EditorSetting::id)
                        .toList());

        assertTrue(
                fixture.registry()
                        .settingsForTool(fixture.context(), "not-selection")
                        .isEmpty());
    }

    private static void assertTool(
            EditorToolRegistration registration,
            String id,
            String label,
            String category,
            String group,
            String icon,
            String shortcut,
            int order
    ) {
        assertEquals(id, registration.id());
        assertEquals(label, registration.label());
        assertEquals(category, registration.category());
        assertEquals(group, registration.toolGroup());
        assertEquals(icon, registration.icon());
        assertEquals(shortcut, registration.shortcut());
        assertEquals(order, registration.order());
    }

    private static Fixture fixture() {
        EditorSession session = new EditorSession(new WorldModel(8, 8, 1));
        EditorPluginRegistry registry = new EditorPluginRegistry();
        EditorPluginContext context = new EditorPluginContext(
                session,
                EmptyAssetRepository.INSTANCE,
                registry);
        return new Fixture(registry, context);
    }

    private record Fixture(
            EditorPluginRegistry registry,
            EditorPluginContext context
    ) {
    }
}
