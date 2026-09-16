package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.tool.AttributeSelectionTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SelectionQueryTest {
    @Test
    void objectAttributeToolSelectsMatchingObjectsAcrossTheDocument() {
        WorldDocument world = new WorldDocument(4, 4, 2);
        WorldObject match = new WorldObject(100, 10, 2, 1, 2, 2);
        WorldObject otherType = new WorldObject(101, 11, 2, 1, 1, 1);
        put(world, match);
        put(world, otherType);
        EditorSession session = new EditorSession(world);
        AttributeSelectionTool tool = new AttributeSelectionTool();
        tool.setObjectId(100);
        tool.setObjectPlane(1);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));

        ObjectSelection selection = assertInstanceOf(ObjectSelection.class, session.selection().current());
        assertEquals(match, selection.object());
    }

    @Test
    void tileAttributeToolSelectsRequiredFlagsAndFloorIds() {
        WorldDocument world = new WorldDocument(4, 4, 2);
        world.tile(1, 2, 3).restore(new TileSnapshot(0, 0, 0, 0, 7, 9, 0, 0, 0x06, List.of()));
        world.tile(1, 1, 1).restore(new TileSnapshot(0, 0, 0, 0, 7, 9, 0, 0, 0x06, List.of()));
        world.tile(0, 2, 3).restore(new TileSnapshot(0, 0, 0, 0, 7, 9, 0, 0, 0x06, List.of()));
        EditorSession session = new EditorSession(world);
        AttributeSelectionTool tool = new AttributeSelectionTool();
        tool.setTarget(AttributeSelectionTool.Target.TILES);
        tool.setTilePlane(1);
        tool.setUnderlayId(7);
        tool.setOverlayId(9);
        tool.setRequiredFlagsMask(0x02);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));

        TileSetSelection selection = assertInstanceOf(TileSetSelection.class, session.selection().current());
        assertEquals(java.util.Set.of(new TileCoordinate(1, 2, 3), new TileCoordinate(1, 1, 1)), selection.coordinates());
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
