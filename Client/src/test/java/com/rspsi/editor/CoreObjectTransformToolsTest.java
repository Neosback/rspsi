package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.tool.TileSnapper;
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

    @Test
    void objectToolsSnapToNearestGridWithoutLeavingTheDocument() {
        WorldDocument world = new WorldDocument(10, 10);
        WorldObject object = new WorldObject(12, 10, 0, 0, 0, 0);
        put(world, object);
        EditorSession session = new EditorSession(world);
        MoveObjectTool tool = new MoveObjectTool();
        tool.setSnapGridSize(4);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(5, 6));
        controller.pointerUp(pointer(5, 6));

        assertEquals(List.of(new WorldObject(12, 10, 0, 0, 4, 8)),
                world.tile(0, 4, 8).snapshot().objects());
        assertEquals(8, TileSnapper.snap(8, 4, 10));
        assertEquals(9, TileSnapper.snap(99, 4, 10));
    }

    @Test
    void duplicateSelectionIsOneAtomicCommandAndSelectsCopies() {
        WorldDocument world = new WorldDocument(8, 8);
        WorldObject first = new WorldObject(12, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(13, 22, 1, 0, 2, 1);
        put(world, first);
        put(world, second);
        EditorSession session = new EditorSession(world);
        session.selection().selectObjects(java.util.Set.of(first, second));
        DuplicateSelectionTool tool = new DuplicateSelectionTool();
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(1, 1));
        controller.pointerDrag(pointer(3, 2));
        controller.pointerUp(pointer(3, 2));

        assertEquals(List.of(new WorldObject(12, 10, 0, 0, 3, 2)),
                world.tile(0, 3, 2).snapshot().objects());
        assertEquals(List.of(new WorldObject(13, 22, 1, 0, 4, 2)),
                world.tile(0, 4, 2).snapshot().objects());
        assertEquals(1, session.history().size());
        session.undo();
        assertEquals(List.of(), world.tile(0, 3, 2).snapshot().objects());
        assertEquals(List.of(), world.tile(0, 4, 2).snapshot().objects());
        assertEquals(2, session.selection().current() instanceof com.rspsi.editor.selection.ObjectSetSelection set
                ? set.objects().size() : -1);
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
