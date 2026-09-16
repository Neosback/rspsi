package com.rspsi.editor;

import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.viewport.Viewport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NeutralEditorContractsTest {

    @Test
    void controllerDispatchesNeutralPointerLifecycle() {
        RecordingTool tool = new RecordingTool();
        EditorToolController controller = new EditorToolController();
        ToolContext context = new ToolContext(new EditorSession(new WorldDocument(2, 2)),
                new EmptyAssets(), (x, y) -> Optional.empty());
        PointerEvent event = new PointerEvent(12.5f, 8.0f, PointerButton.PRIMARY, true, false, true);

        controller.activate(tool, context);
        controller.pointerDown(event);
        controller.pointerDrag(event);
        controller.pointerUp(event);
        controller.deactivate();

        assertEquals(List.of("activate", "down", "drag", "up", "deactivate"), tool.events);
        assertSame(event, tool.lastEvent);
        assertNull(controller.activeTool());
    }

    @Test
    void pointerAndRendererValuesRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new PointerEvent(Float.NaN, 1, PointerButton.NONE, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new com.rspsi.editor.render.CameraState(0, 0, 0, Float.POSITIVE_INFINITY, 0));
    }

    private static final class RecordingTool implements EditorTool {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();
        private PointerEvent lastEvent;

        @Override public String id() { return "test"; }
        @Override public void activate(ToolContext context) { events.add("activate"); }
        @Override public void deactivate() { events.add("deactivate"); }
        @Override public void pointerDown(PointerEvent event) { events.add("down"); lastEvent = event; }
        @Override public void pointerDrag(PointerEvent event) { events.add("drag"); lastEvent = event; }
        @Override public void pointerUp(PointerEvent event) { events.add("up"); lastEvent = event; }
    }

    private static final class EmptyAssets implements AssetRepository {
        @Override public List<com.rspsi.editor.assets.AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<com.rspsi.editor.assets.AssetDescriptor> get(int id, String type) {
            return Optional.empty();
        }
    }
}
