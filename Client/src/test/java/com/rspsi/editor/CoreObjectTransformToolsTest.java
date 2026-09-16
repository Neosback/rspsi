package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreObjectTransformToolsTest {
    @Test
    void moveToolCommitsOnReleaseAndSupportsUndo() {
        WorldDocument world = new WorldDocument(4, 4);
        WorldObject object = new WorldObject(12, 10, 0, 0, 0, 0);
        put(world, object);
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();
        controller.activate(new MoveObjectTool(), context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(2, 1));
        controller.pointerUp(pointer(2, 1));

        assertEquals(List.of(), world.tile(0, 0, 0).snapshot().objects());
        assertEquals(List.of(new WorldObject(12, 10, 0, 0, 2, 1)),
                world.tile(0, 2, 1).snapshot().objects());
        assertEquals(1, session.history().size());
        session.undo();
        assertEquals(List.of(object), world.tile(0, 0, 0).snapshot().objects());
    }

    @Test
    void duplicateToolUsesASeparateCommandAndUndoEntry() {
        WorldDocument world = new WorldDocument(4, 4);
        WorldObject object = new WorldObject(12, 10, 0, 0, 0, 0);
        put(world, object);
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();
        controller.activate(new DuplicateObjectTool(), context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(1, 1));
        controller.pointerUp(pointer(1, 1));

        assertEquals(List.of(new WorldObject(12, 10, 0, 0, 1, 1)),
                world.tile(0, 1, 1).snapshot().objects());
        session.undo();
        assertEquals(List.of(), world.tile(0, 1, 1).snapshot().objects());
    }

    private static void put(WorldDocument world, WorldObject object) {
        world.tile(object.plane(), object.x(), object.y()).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(object)));
    }
    private static ToolContext context(EditorSession session) {
        return new ToolContext(session, new EmptyAssets(),
                (x, y) -> Optional.of(new TileCoordinate(0, (int) x, (int) y)));
    }
    private static PointerEvent pointer(float x, float y) {
        return new PointerEvent(x, y, PointerButton.PRIMARY, false, false, false);
    }
    private static final class EmptyAssets implements com.rspsi.editor.assets.AssetRepository {
        @Override public List<com.rspsi.editor.assets.AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<com.rspsi.editor.assets.AssetDescriptor> get(int id, String type) { return Optional.empty(); }
    }
}
