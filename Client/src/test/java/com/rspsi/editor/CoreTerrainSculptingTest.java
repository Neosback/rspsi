package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.TerrainHeightSampler;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreTerrainSculptingTest {
    @Test
    void flattenBrushGroupsTheStrokeAndRemainsUndoable() {
        WorldDocument world = new WorldDocument(2, 2);
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();
        controller.activate(new FlattenTerrainTool(64), context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(1, 0));
        controller.pointerUp(pointer(1, 0));

        assertEquals(1, session.history().size());
        assertEquals(64, world.tile(0, 0, 0).snapshot().southWestHeight());
        assertEquals(64, world.tile(0, 1, 0).snapshot().northWestHeight());
        session.undo();
        assertEquals(0, world.tile(0, 0, 0).snapshot().southWestHeight());
    }

    @Test
    void smoothBrushUsesNeighbourHeights() {
        WorldDocument world = new WorldDocument(3, 3);
        world.tile(0, 1, 1).restore(new TileSnapshot(100, 100, 100, 100,
                0, 0, 0, 0, 0, List.of()));
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();
        controller.activate(new SmoothTerrainTool(100), context(session));
        controller.pointerDown(pointer(1, 1));
        controller.pointerUp(pointer(1, 1));

        assertEquals(25, world.tile(0, 1, 1).snapshot().southWestHeight());
        assertEquals(25, world.tile(0, 1, 1).snapshot().northEastHeight());
        assertEquals(1, session.history().size());
    }

    @Test
    void falloffBrushUpdatesSharedVerticesWithoutTerrainCracks() {
        WorldDocument world = new WorldDocument(5, 5);
        EditorSession session = new EditorSession(world);
        ChangeHeightTool tool = new ChangeHeightTool(9);
        tool.setRadius(1);
        tool.setFalloff(ChangeHeightTool.Falloff.LINEAR);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(2, 2));
        controller.pointerUp(pointer(2, 2));

        assertEquals(9, world.tile(0, 2, 2).snapshot().southWestHeight());
        assertEquals(9, world.tile(0, 3, 2).snapshot().southWestHeight());
        assertEquals(5, world.tile(0, 3, 2).snapshot().southEastHeight());
        assertEquals(world.tile(0, 2, 2).snapshot().southEastHeight(),
                world.tile(0, 3, 2).snapshot().southWestHeight());
        assertEquals(1, session.history().size());
        session.undo();
        assertEquals(0, world.tile(0, 3, 2).snapshot().southWestHeight());
    }

    @Test
    void rampInterpolatesSharedVerticesAndCanBeSampled() {
        WorldDocument world = new WorldDocument(4, 2);
        EditorSession session = new EditorSession(world);
        RampTerrainTool tool = new RampTerrainTool(0, 100);
        tool.setAxis(RampTerrainTool.Axis.X);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerDrag(pointer(2, 1));
        controller.pointerUp(pointer(2, 1));

        assertEquals(33, world.tile(0, 1, 0).snapshot().southWestHeight());
        assertEquals(67, world.tile(0, 1, 0).snapshot().southEastHeight());
        assertEquals(67, world.tile(0, 2, 0).snapshot().southWestHeight());
        assertEquals(50, TerrainHeightSampler.sample(world.tile(0, 1, 0).snapshot(), .5, .5));
        assertEquals(1, session.history().size());
        session.undo();
        assertEquals(0, world.tile(0, 2, 0).snapshot().southWestHeight());
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
