package com.rspsi.editor.tool;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EditorToolControllerTest {

    @Test
    void activationDispatchAndReplacementPreserveLifecycle() {
        EditorToolController controller = new EditorToolController();
        ToolContext context = context();
        RecordingTool first = new RecordingTool("first");
        RecordingTool second = new RecordingTool("second");
        PointerEvent event = new PointerEvent(
                3.0f, 5.0f, PointerButton.PRIMARY, false, false, false);

        assertNull(controller.activeTool());

        controller.activate(first, context);
        assertSame(first, controller.activeTool());
        assertEquals(1, first.activations);
        assertSame(context, first.lastContext);

        controller.pointerDown(event);
        controller.pointerDrag(event);
        controller.pointerMove(event);
        controller.pointerUp(event);

        assertEquals(1, first.pointerDowns);
        assertEquals(1, first.pointerDrags);
        assertEquals(1, first.pointerMoves);
        assertEquals(1, first.pointerUps);
        assertSame(event, first.lastEvent);

        controller.activate(second, context);
        assertEquals(1, first.deactivations);
        assertEquals(1, second.activations);
        assertSame(second, controller.activeTool());

        controller.deactivate();
        assertEquals(1, second.deactivations);
        assertNull(controller.activeTool());

        controller.deactivate();
        assertEquals(1, second.deactivations, "Repeated deactivation must stay idempotent");
    }

    @Test
    void invalidReplacementDoesNotDeactivateCurrentTool() {
        EditorToolController controller = new EditorToolController();
        ToolContext context = context();
        RecordingTool tool = new RecordingTool("active");

        controller.activate(tool, context);

        assertThrows(NullPointerException.class, () -> controller.activate(null, context));
        assertThrows(NullPointerException.class, () -> controller.activate(tool, null));

        assertSame(tool, controller.activeTool());
        assertEquals(0, tool.deactivations);
        assertEquals(1, tool.activations);
    }

    private static ToolContext context() {
        EditorSession session = new EditorSession(new WorldDocument(16, 16));
        return new ToolContext(
                session,
                AssetRepository.empty(),
                (x, y) -> Optional.of(new WorldTile(0, (int) x, (int) y)));
    }

    private static final class RecordingTool implements EditorTool {
        private final String id;
        private int activations;
        private int deactivations;
        private int pointerDowns;
        private int pointerDrags;
        private int pointerUps;
        private int pointerMoves;
        private ToolContext lastContext;
        private PointerEvent lastEvent;

        private RecordingTool(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void activate(ToolContext context) {
            activations++;
            lastContext = context;
        }

        @Override
        public void deactivate() {
            deactivations++;
        }

        @Override
        public void pointerDown(PointerEvent event) {
            pointerDowns++;
            lastEvent = event;
        }

        @Override
        public void pointerDrag(PointerEvent event) {
            pointerDrags++;
            lastEvent = event;
        }

        @Override
        public void pointerUp(PointerEvent event) {
            pointerUps++;
            lastEvent = event;
        }

        @Override
        public void pointerMove(PointerEvent event) {
            pointerMoves++;
            lastEvent = event;
        }
    }
}
