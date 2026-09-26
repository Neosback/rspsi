package com.rspsi.editor.core.module;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.core.inspector.ObjectContentInspector;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorInspectorRegistration;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreObjectModuleCompatibilityTest {

    @Test
    void moduleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        int modifiers = CoreObjectModule.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertTrue(Modifier.isPublic(
                CoreObjectModule.class.getDeclaredConstructor().getModifiers()));

        var idField = CoreObjectModule.class.getDeclaredField("ID");
        int idModifiers = idField.getModifiers();
        assertTrue(Modifier.isPublic(idModifiers));
        assertTrue(Modifier.isStatic(idModifiers));
        assertTrue(Modifier.isFinal(idModifiers));
        assertEquals("rspsi.tools.objects", idField.get(null));

        CoreObjectModule module = new CoreObjectModule();
        assertEquals("rspsi.tools.objects", module.id());
        assertEquals(20, module.order());
        assertEquals("0.1.0", module.version());
    }

    @Test
    void installRegistersExactObjectToolInventoryAndMetadata() {
        Fixture fixture = fixture();
        new CoreObjectModule().install(fixture.context());

        List<EditorToolRegistration> tools = fixture.registry().toolRegistrations();
        assertEquals(5, tools.size());

        assertTool(tools.get(0),
                "object.place", "Place object", "Objects", "Objects",
                "ADD", "4", 10);
        assertTool(tools.get(1),
                "object.move", "Move object", "Objects", "Objects",
                "MOVE", "Shift+4", 20);
        assertTool(tools.get(2),
                "object.rotate", "Rotate object", "Objects", "Objects",
                "ROTATE", null, 30);
        assertTool(tools.get(3),
                "object.duplicate", "Duplicate object", "Objects", "Objects",
                "DUPLICATE", null, 40);
        assertTool(tools.get(4),
                "object.delete", "Delete object", "Objects", "Objects",
                "DELETE", "Delete", 50);
    }

    @Test
    void factoriesCreateExpectedToolsAndApplyCurrentSettingsLazily() {
        Fixture fixture = fixture();
        new CoreObjectModule().install(fixture.context());

        fixture.context().settings().set(EditorSettingKeys.OBJECT_ID, 321);
        fixture.context().settings().set(EditorSettingKeys.OBJECT_TYPE, 22);
        fixture.context().settings().set(EditorSettingKeys.OBJECT_ROTATION, 3);
        fixture.context().settings().set(EditorSettingKeys.OBJECT_QUARTER_TURNS, 2);
        fixture.context().settings().set(EditorSettingKeys.OBJECT_SNAP_GRID, 8);

        PlaceObjectTool place = assertInstanceOf(
                PlaceObjectTool.class,
                fixture.registry().createTool("object.place"));
        assertEquals(321, place.idValue());
        assertEquals(22, place.type());
        assertEquals(3, place.rotation());

        MoveObjectTool move = assertInstanceOf(
                MoveObjectTool.class,
                fixture.registry().createTool("object.move"));
        assertEquals(8, move.snapGridSize());

        RotateObjectTool rotate = assertInstanceOf(
                RotateObjectTool.class,
                fixture.registry().createTool("object.rotate"));
        assertEquals(2, rotate.quarterTurns());

        DuplicateObjectTool duplicate = assertInstanceOf(
                DuplicateObjectTool.class,
                fixture.registry().createTool("object.duplicate"));
        assertEquals(8, duplicate.snapGridSize());

        assertInstanceOf(
                DeleteObjectTool.class,
                fixture.registry().createTool("object.delete"));
    }

    @Test
    void installRegistersExactObjectSettingsContext() {
        Fixture fixture = fixture();
        new CoreObjectModule().install(fixture.context());

        List<EditorToolContextRegistration> contexts =
                fixture.registry().toolContextRegistrations();
        assertEquals(1, contexts.size());

        EditorToolContextRegistration context = contexts.get(0);
        assertEquals("objects.context", context.id());
        assertEquals("Object settings", context.label());
        assertEquals(0, context.order());
        assertEquals(
                List.of(
                        "object.place",
                        "object.move",
                        "object.rotate",
                        "object.duplicate",
                        "object.delete"),
                context.toolIds());

        assertEquals(
                List.of(
                        "objects.id",
                        "objects.type",
                        "objects.rotation",
                        "objects.quarter-turns",
                        "objects.snap-grid"),
                fixture.registry()
                        .settingsForTool(fixture.context(), "object.place")
                        .stream()
                        .map(com.rspsi.editor.plugin.EditorSetting::id)
                        .toList());

        assertTrue(
                fixture.registry()
                        .settingsForTool(fixture.context(), "not-object")
                        .isEmpty());
    }

    @Test
    void installRegistersExactObjectContentInspector() {
        Fixture fixture = fixture();
        new CoreObjectModule().install(fixture.context());

        List<EditorInspectorRegistration> inspectors =
                fixture.registry().inspectorRegistrations();
        assertEquals(1, inspectors.size());

        EditorInspectorRegistration inspector = inspectors.get(0);
        assertEquals("objects.content", inspector.id());
        assertEquals("Server content", inspector.label());
        assertEquals("Objects", inspector.category());

        assertInstanceOf(
                ObjectContentInspector.class,
                fixture.registry().createInspector("objects.content"));
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
