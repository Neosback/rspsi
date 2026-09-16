package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.tool.MoveSelectionTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CoreBoxSelectToolTest {
    @Test
    void boxSelectCreatesAnAreaSelection() {
        EditorSession session = new EditorSession(new WorldDocument(8, 8));
        EditorToolController controller = new EditorToolController();
        controller.activate(new BoxSelectTool(), context(session));
        controller.pointerDown(pointer(5, 6));
        controller.pointerDrag(pointer(2, 3));
        controller.pointerUp(pointer(2, 3));

        TileAreaSelection selection = assertInstanceOf(TileAreaSelection.class, session.selection().current());
        assertEquals(4, selection.bounds().width());
        assertEquals(4, selection.bounds().height());
    }

    @Test
    void objectModeSelectsAllObjectsInTheBox() {
        WorldDocument world = new WorldDocument(8, 8);
        WorldObject first = new WorldObject(7, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(8, 10, 0, 0, 3, 4);
        put(world, first);
        put(world, second);
        EditorSession session = new EditorSession(world);
        BoxSelectTool tool = new BoxSelectTool();
        tool.setTarget(BoxSelectTool.Target.OBJECTS);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(4, 4));
        controller.pointerUp(pointer(4, 4));

        ObjectSetSelection selection = assertInstanceOf(ObjectSetSelection.class, session.selection().current());
        assertEquals(java.util.Set.of(first, second), selection.objects());
    }

    @Test
    void moveSelectionMovesAllObjectsAsOneUndoableCommand() {
        WorldDocument world = new WorldDocument(8, 8);
        WorldObject first = new WorldObject(7, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(8, 10, 1, 0, 2, 2);
        put(world, first);
        put(world, second);
        EditorSession session = new EditorSession(world);
        session.selection().selectObjects(java.util.Set.of(first, second));
        EditorToolController controller = new EditorToolController();
        controller.activate(new MoveSelectionTool(), context(session));
        controller.pointerDown(pointer(1, 1));
        controller.pointerDrag(pointer(3, 4));
        controller.pointerUp(pointer(3, 4));

        assertEquals(List.of(), world.tile(0, 1, 1).snapshot().objects());
        assertEquals(List.of(), world.tile(0, 2, 2).snapshot().objects());
        assertEquals(List.of(new WorldObject(7, 10, 0, 0, 3, 4)),
                world.tile(0, 3, 4).snapshot().objects());
        assertEquals(List.of(new WorldObject(8, 10, 1, 0, 4, 5)),
                world.tile(0, 4, 5).snapshot().objects());
        assertEquals(1, session.history().size());
        session.undo();
        assertEquals(List.of(first), world.tile(0, 1, 1).snapshot().objects());
        assertEquals(List.of(second), world.tile(0, 2, 2).snapshot().objects());
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
