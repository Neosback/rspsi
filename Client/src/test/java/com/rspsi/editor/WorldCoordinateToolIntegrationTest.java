package com.rspsi.editor;

import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the absolute-world vs document-local bug class.
 *
 * <p>All picks are in region 50,50 (world origin 3200,3200). A tool that
 * accidentally feeds those coordinates directly into a 64x64 WorldDocument
 * fails immediately.</p>
 */
class WorldCoordinateToolIntegrationTest {
    private static final int ORIGIN = 50 * 64;

    @Test
    void terrainPaintConvertsWorldPickExactlyOnce() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        EditorSession session = session(world);
        EditorToolController controller = new EditorToolController();

        controller.activate(new PaintUnderlayTool(17),
                context(session, new WorldTile(0, ORIGIN + 5, ORIGIN + 6)));
        controller.pointerDown(click());
        controller.pointerUp(click());

        assertEquals(17, world.tile(0, 5, 6).snapshot().underlayId());
        assertEquals(0, world.tile(0, 0, 0).snapshot().underlayId());
        assertEquals(1, session.history().size());
        assertTrue(session.undo());
        assertEquals(0, world.tile(0, 5, 6).snapshot().underlayId());
    }

    @Test
    void heightBrushUsesLocalVertexLatticeFromWorldPick() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        EditorSession session = session(world);
        EditorToolController controller = new EditorToolController();

        controller.activate(new ChangeHeightTool(8),
                context(session, new WorldTile(0, ORIGIN + 12, ORIGIN + 13)));
        controller.pointerDown(click());
        controller.pointerUp(click());

        assertEquals(8, world.tile(0, 12, 13).snapshot().southWestHeight());
        assertEquals(8, world.tile(0, 11, 12).snapshot().northEastHeight());
    }

    @Test
    void objectPlacementStoresDocumentLocalCoordinates() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        EditorSession session = session(world);
        EditorToolController controller = new EditorToolController();

        controller.activate(new PlaceObjectTool(100, 10, 2),
                context(session, new WorldTile(0, ORIGIN + 20, ORIGIN + 21)));
        controller.pointerDown(click());

        assertEquals(List.of(new WorldObject(100, 10, 2, 0, 20, 21)),
                world.tile(0, 20, 21).snapshot().objects());
    }

    @Test
    void boxSelectionConvertsWorldMarqueeToLocalBounds() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        EditorSession session = session(world);
        EditorToolController controller = new EditorToolController();

        WorldTile[] current = {new WorldTile(0, ORIGIN + 2, ORIGIN + 3)};
        ToolContext context = new ToolContext(session, new EmptyAssets(),
                (x, y) -> Optional.of(current[0]));
        controller.activate(new BoxSelectTool(), context);
        controller.pointerDown(click());
        current[0] = new WorldTile(0, ORIGIN + 5, ORIGIN + 7);
        controller.pointerDrag(click());
        controller.pointerUp(click());

        TileAreaSelection selection = assertInstanceOf(
                TileAreaSelection.class, session.selection().current());
        assertEquals(2, selection.bounds().minX());
        assertEquals(3, selection.bounds().minY());
        assertEquals(5, selection.bounds().maxX());
        assertEquals(7, selection.bounds().maxY());
    }

    @Test
    void worldPickOutsideActiveDocumentIsIgnoredInsteadOfWrapped() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        EditorSession session = session(world);
        TileSnapshot before = world.tile(0, 0, 0).snapshot();
        EditorToolController controller = new EditorToolController();

        controller.activate(new PaintUnderlayTool(99),
                context(session, new WorldTile(0, ORIGIN + 64, ORIGIN)));
        controller.pointerDown(click());
        controller.pointerUp(click());

        assertEquals(before, world.tile(0, 0, 0).snapshot());
        assertEquals(0, session.history().size());
    }

    private static EditorSession session(WorldDocument world) {
        return new EditorSession(world, new WorldWindow(ORIGIN, ORIGIN, 64, 64));
    }

    private static ToolContext context(EditorSession session, WorldTile pick) {
        return new ToolContext(session, new EmptyAssets(), (x, y) -> Optional.of(pick));
    }

    private static PointerEvent click() {
        return new PointerEvent(1, 1, PointerButton.PRIMARY, false, false, false);
    }

    private static final class EmptyAssets implements AssetRepository {
        @Override public List<AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<AssetDescriptor> get(int id, String type) {
            return Optional.empty();
        }
    }
}
