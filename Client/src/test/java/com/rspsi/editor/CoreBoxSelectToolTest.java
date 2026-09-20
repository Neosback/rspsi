package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.LassoSelectTool;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
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
    void lassoSelectsTileCentersAndUsesUnifiedTileSetSelection() {
        EditorSession session = new EditorSession(new WorldDocument(8, 8));
        EditorToolController controller = new EditorToolController();
        controller.activate(new LassoSelectTool(), context(session));
        controller.pointerDown(pointer(1, 1));
        controller.pointerDrag(pointer(5, 1));
        controller.pointerDrag(pointer(5, 5));
        controller.pointerDrag(pointer(1, 5));
        controller.pointerUp(pointer(1, 5));

        com.rspsi.editor.selection.TileSetSelection selection =
                assertInstanceOf(com.rspsi.editor.selection.TileSetSelection.class, session.selection().current());
        assertEquals(java.util.Set.of(
                new TileCoordinate(0, 1, 1), new TileCoordinate(0, 1, 2), new TileCoordinate(0, 1, 3),
                new TileCoordinate(0, 1, 4),
                new TileCoordinate(0, 2, 1), new TileCoordinate(0, 2, 2), new TileCoordinate(0, 2, 3),
                new TileCoordinate(0, 2, 4),
                new TileCoordinate(0, 3, 1), new TileCoordinate(0, 3, 2), new TileCoordinate(0, 3, 3),
                new TileCoordinate(0, 3, 4),
                new TileCoordinate(0, 4, 1), new TileCoordinate(0, 4, 2), new TileCoordinate(0, 4, 3),
                new TileCoordinate(0, 4, 4)),
                selection.coordinates());
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

    @Test
    void rotateSelectionUpdatesAllObjectsAndKeepsSelectionCurrent() {
        WorldDocument world = new WorldDocument(8, 8);
        WorldObject first = new WorldObject(7, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(8, 10, 2, 0, 2, 2);
        put(world, first);
        put(world, second);
        EditorSession session = new EditorSession(world);
        session.selection().selectObjects(java.util.Set.of(first, second));
        EditorToolController controller = new EditorToolController();
        controller.activate(new RotateSelectionTool(), context(session));
        controller.pointerDown(pointer(1, 1));

        WorldObject rotatedFirst = new WorldObject(7, 10, 1, 0, 1, 1);
        WorldObject rotatedSecond = new WorldObject(8, 10, 3, 0, 2, 2);
        assertEquals(List.of(rotatedFirst), world.tile(0, 1, 1).snapshot().objects());
        assertEquals(List.of(rotatedSecond), world.tile(0, 2, 2).snapshot().objects());
        assertEquals(java.util.Set.of(rotatedFirst, rotatedSecond),
                ((com.rspsi.editor.selection.ObjectSetSelection) session.selection().current()).objects());
        session.undo();
        assertEquals(List.of(first), world.tile(0, 1, 1).snapshot().objects());
        assertEquals(List.of(second), world.tile(0, 2, 2).snapshot().objects());
    }

    @Test
    void replaceSelectionChangesOnlyDefinitionIDsAndIsUndoable() {
        WorldDocument world = new WorldDocument(8, 8);
        WorldObject first = new WorldObject(7, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(8, 22, 3, 0, 2, 2);
        put(world, first);
        put(world, second);
        EditorSession session = new EditorSession(world);
        session.selection().selectObjects(java.util.Set.of(first, second));
        EditorToolController controller = new EditorToolController();
        controller.activate(new ReplaceSelectionTool(99), context(session));
        controller.pointerDown(pointer(1, 1));

        WorldObject replacedFirst = new WorldObject(99, 10, 0, 0, 1, 1);
        WorldObject replacedSecond = new WorldObject(99, 22, 3, 0, 2, 2);
        assertEquals(List.of(replacedFirst), world.tile(0, 1, 1).snapshot().objects());
        assertEquals(List.of(replacedSecond), world.tile(0, 2, 2).snapshot().objects());
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
