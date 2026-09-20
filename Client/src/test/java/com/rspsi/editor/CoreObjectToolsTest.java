package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.viewport.Viewport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CoreObjectToolsTest {
    @Test
    void placeRotateDeleteToolsUseSessionCommands() {
        WorldDocument world = new WorldDocument(3, 3);
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();
        ToolContext context = new ToolContext(session, new EmptyAssets(),
                (x, y) -> Optional.of(new WorldTile(0, (int) x, (int) y)));
        PointerEvent click = new PointerEvent(1, 1, PointerButton.PRIMARY, false, false, false);

        controller.activate(new PlaceObjectTool(100, 10, 0), context);
        controller.pointerDown(click);
        assertEquals(1, session.history().size());
        controller.activate(new RotateObjectTool(), context);
        controller.pointerDown(click);
        assertEquals(1, world.tile(0, 1, 1).snapshot().objects().get(0).rotation());
        controller.activate(new DeleteObjectTool(), context);
        controller.pointerDown(click);
        assertTrue(world.tile(0, 1, 1).snapshot().objects().isEmpty());
        assertEquals(3, session.history().size());
    }

    @Test
    void deleteToolUsesObjectAwareViewportBeforeTileFallback() {
        WorldDocument world = new WorldDocument(2, 2);
        WorldObject first = new WorldObject(100, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(101, 10, 0, 0, 1, 1);
        world.tile(0, 1, 1).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(first, second)));
        EditorSession session = new EditorSession(world);
        Viewport viewport = new Viewport() {
            @Override public Optional<WorldTile> tileAt(float x, float y) {
                return Optional.of(new WorldTile(0, 1, 1));
            }

            @Override public Optional<WorldObject> objectAt(float x, float y) {
                return Optional.of(second);
            }
        };
        EditorToolController controller = new EditorToolController();
        controller.activate(new DeleteObjectTool(), new ToolContext(session, new EmptyAssets(), viewport));

        controller.pointerDown(new PointerEvent(1, 1, PointerButton.PRIMARY, false, false, false));

        assertEquals(List.of(first), world.tile(0, 1, 1).snapshot().objects());
    }

    @Test
    void groupedObjectDeletionIsOneUndoableSessionEdit() {
        WorldDocument world = new WorldDocument(3, 3);
        WorldObject first = new WorldObject(100, 10, 0, 0, 0, 0);
        WorldObject second = new WorldObject(101, 10, 1, 0, 2, 2);
        world.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(first)));
        world.tile(0, 2, 2).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(second)));
        EditorSession session = new EditorSession(world);

        session.execute(new CompositeEditCommand("Delete selected objects", List.of(
                new DeleteObjectCommand(first), new DeleteObjectCommand(second))));

        assertTrue(world.tile(0, 0, 0).snapshot().objects().isEmpty());
        assertTrue(world.tile(0, 2, 2).snapshot().objects().isEmpty());
        assertEquals(1, session.history().size());
        assertTrue(session.undo());
        assertEquals(List.of(first), world.tile(0, 0, 0).snapshot().objects());
        assertEquals(List.of(second), world.tile(0, 2, 2).snapshot().objects());
    }

    private static final class EmptyAssets implements com.rspsi.editor.assets.AssetRepository {
        @Override public List<com.rspsi.editor.assets.AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<com.rspsi.editor.assets.AssetDescriptor> get(int id, String type) { return Optional.empty(); }
    }
}
