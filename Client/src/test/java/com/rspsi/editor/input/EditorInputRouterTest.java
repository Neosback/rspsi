package com.rspsi.editor.input;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorShortcutRegistration;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorInputRouterTest {

    @Test
    void textFocusAndKeyUpEventsDoNotDispatchGlobalShortcuts() {
        EditorPluginRegistry registry = new EditorPluginRegistry();
        AtomicInteger handled = new AtomicInteger();
        registry.registerShortcut(new EditorShortcutRegistration(
                "test.shortcut",
                "Test Shortcut",
                "K",
                false,
                true,
                false,
                false,
                () -> (context, event) -> {
                    handled.incrementAndGet();
                    return true;
                }));

        EditorInputRouter router = new EditorInputRouter(pluginContext(registry), new EditorToolController());

        assertFalse(router.key(new EditorKeyEvent(
                "K", true, false, false, true, false, false), true));
        assertFalse(router.key(new EditorKeyEvent(
                "K", false, false, false, true, false, false)));

        assertEquals(0, handled.get());
    }

    @Test
    void matchingPressedShortcutDispatchesThroughRegistry() {
        EditorPluginRegistry registry = new EditorPluginRegistry();
        AtomicInteger handled = new AtomicInteger();
        registry.registerShortcut(new EditorShortcutRegistration(
                "test.shortcut",
                "Test Shortcut",
                "K",
                false,
                true,
                false,
                false,
                () -> (context, event) -> {
                    handled.incrementAndGet();
                    return true;
                }));

        EditorInputRouter router = new EditorInputRouter(pluginContext(registry), new EditorToolController());

        assertTrue(router.key(new EditorKeyEvent(
                "K", true, false, false, true, false, false)));
        assertEquals(1, handled.get());
    }

    @Test
    void pointerLifecycleIsForwardedUnchangedToActiveTool() {
        EditorToolController controller = new EditorToolController();
        RecordingTool tool = new RecordingTool();
        ToolContext toolContext = toolContext();
        controller.activate(tool, toolContext);

        EditorInputRouter router =
                new EditorInputRouter(pluginContext(new EditorPluginRegistry()), controller);
        PointerEvent event =
                new PointerEvent(4.0f, 7.0f, PointerButton.PRIMARY, false, false, false);

        router.pointerDown(event);
        router.pointerDrag(event);
        router.pointerMove(event);
        router.pointerUp(event);

        assertSame(controller, router.tools());
        assertSame(event, tool.down);
        assertSame(event, tool.drag);
        assertSame(event, tool.move);
        assertSame(event, tool.up);
    }

    @Test
    void constructorAndKeyNullContractsStayJavaCompatible() {
        EditorPluginContext context = pluginContext(new EditorPluginRegistry());
        EditorToolController controller = new EditorToolController();

        assertThrows(NullPointerException.class, () -> new EditorInputRouter(null, controller));
        assertThrows(NullPointerException.class, () -> new EditorInputRouter(context, null));

        EditorInputRouter router = new EditorInputRouter(context, controller);
        assertThrows(NullPointerException.class, () -> router.key(null));
        assertThrows(NullPointerException.class, () -> router.key(null, true));
    }

    private static EditorPluginContext pluginContext(EditorPluginRegistry registry) {
        EditorSession session = new EditorSession(new WorldDocument(16, 16));
        return new EditorPluginContext(session, AssetRepository.empty(), registry);
    }

    private static ToolContext toolContext() {
        EditorSession session = new EditorSession(new WorldDocument(16, 16));
        return new ToolContext(
                session,
                AssetRepository.empty(),
                (x, y) -> Optional.of(new WorldTile(0, (int) x, (int) y)));
    }

    private static final class RecordingTool implements EditorTool {
        private PointerEvent down;
        private PointerEvent drag;
        private PointerEvent up;
        private PointerEvent move;

        @Override
        public String id() {
            return "recording";
        }

        @Override
        public void activate(ToolContext context) {
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void pointerDown(PointerEvent event) {
            down = event;
        }

        @Override
        public void pointerDrag(PointerEvent event) {
            drag = event;
        }

        @Override
        public void pointerUp(PointerEvent event) {
            up = event;
        }

        @Override
        public void pointerMove(PointerEvent event) {
            move = event;
        }
    }
}
