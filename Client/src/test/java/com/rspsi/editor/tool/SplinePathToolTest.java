package com.rspsi.editor.tool;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.tool.spline.SplineBrushStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SplinePathToolTest {

    @Test
    void interactiveNodePlacementAndDrag() {
        WorldDocument world = new WorldDocument(32, 32);
        EditorSession session = new EditorSession(world);
        SplinePathTool tool = new SplinePathTool();
        tool.activate(context(session));

        // Click at (5, 5) -> node 1
        tool.pointerDown(pointer(5, 5, PointerButton.PRIMARY, false));
        tool.pointerUp(pointer(5, 5, PointerButton.PRIMARY, false));
        assertEquals(1, tool.path().size());

        // Click at (15, 15) -> node 2
        tool.pointerDown(pointer(15, 15, PointerButton.PRIMARY, false));
        tool.pointerUp(pointer(15, 15, PointerButton.PRIMARY, false));
        assertEquals(2, tool.path().size());

        // Drag node 2 to (16, 16)
        tool.pointerDown(pointer(15, 15, PointerButton.PRIMARY, false));
        tool.pointerDrag(pointer(16, 16, PointerButton.PRIMARY, false));
        tool.pointerUp(pointer(16, 16, PointerButton.PRIMARY, false));
        assertEquals(16, tool.path().points().get(1).x);
        assertEquals(16, tool.path().points().get(1).y);

        // Right-click node 1 to delete it
        tool.pointerDown(pointer(5, 5, PointerButton.SECONDARY, false));
        assertEquals(1, tool.path().size());
        assertEquals(16, tool.path().points().get(0).x);
    }

    @Test
    void shiftClickAlwaysAddsNodeEvenWhenNearExisting() {
        WorldDocument world = new WorldDocument(32, 32);
        EditorSession session = new EditorSession(world);
        SplinePathTool tool = new SplinePathTool();
        tool.activate(context(session));

        // Normal click at (10, 10)
        tool.pointerDown(pointer(10, 10, PointerButton.PRIMARY, false));
        tool.pointerUp(pointer(10, 10, PointerButton.PRIMARY, false));
        assertEquals(1, tool.path().size());

        // Normal click at (10, 10) selects existing node for dragging, does not add a new node
        tool.pointerDown(pointer(10, 10, PointerButton.PRIMARY, false));
        assertEquals(1, tool.path().size());
        tool.pointerUp(pointer(10, 10, PointerButton.PRIMARY, false));

        // Shift-click at (10, 10) forces adding a new node
        tool.pointerDown(new PointerEvent(10, 10, PointerButton.PRIMARY, true, false, false));
        tool.pointerUp(new PointerEvent(10, 10, PointerButton.PRIMARY, true, false, false));
        assertEquals(2, tool.path().size());
    }

    @Test
    void buildPathAndUndoRedo() {
        WorldDocument world = new WorldDocument(32, 32);
        EditorSession session = new EditorSession(world);
        SplinePathTool tool = new SplinePathTool();
        tool.setOverlayId(42);
        tool.setWidth(2);
        tool.setStyle(SplineBrushStyle.SMOOTH);
        tool.activate(context(session));

        // Initial check: tile (10, 10) has no overlay (0)
        assertEquals(0, world.tile(0, 10, 10).snapshot().overlayId());

        // Plot 3 points passing through (10, 10)
        tool.pointerDown(pointer(2, 10, PointerButton.PRIMARY, false));
        tool.pointerDown(pointer(10, 10, PointerButton.PRIMARY, false));
        tool.pointerDown(pointer(20, 10, PointerButton.PRIMARY, false));
        assertEquals(3, tool.path().size());

        // Build path
        boolean built = tool.buildPath();
        assertTrue(built);
        assertTrue(tool.path().isEmpty(), "Active spline path should be cleared after building");

        // Tile (10, 10) must now have overlay 42
        TileSnapshot afterSnapshot = world.tile(0, 10, 10).snapshot();
        assertEquals(42, afterSnapshot.overlayId());

        // Undo -> reverts to 0
        session.undo();
        assertEquals(0, world.tile(0, 10, 10).snapshot().overlayId());

        // Redo -> re-applies 42
        session.redo();
        assertEquals(42, world.tile(0, 10, 10).snapshot().overlayId());
    }

    @Test
    void pointerMoveTracksHoveredTile() {
        WorldDocument world = new WorldDocument(32, 32);
        EditorSession session = new EditorSession(world);
        SplinePathTool tool = new SplinePathTool();
        tool.activate(context(session));

        assertNull(tool.hoveredTile());
        tool.pointerMove(new PointerEvent(14.0f, 22.0f, PointerButton.PRIMARY, false, false, false));
        assertNotNull(tool.hoveredTile());
        assertEquals(14, tool.hoveredTile().x());
        assertEquals(22, tool.hoveredTile().y());

        tool.deactivate();
        assertNull(tool.hoveredTile());
    }

    @Test
    void rampStyleInterpolatesTerrainHeights() {
        WorldDocument world = new WorldDocument(32, 32);
        // Set initial heights: tile (2, 5) height=0, tile (20, 5) height=128
        world.tile(0, 2, 5).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
        world.tile(0, 20, 5).restore(new TileSnapshot(128, 128, 128, 128, 0, 0, 0, 0, 0, List.of()));

        EditorSession session = new EditorSession(world);
        SplinePathTool tool = new SplinePathTool();
        tool.setStyle(SplineBrushStyle.RAMP);
        tool.setOverlayId(10);
        tool.activate(context(session));

        tool.pointerDown(pointer(2, 5, PointerButton.PRIMARY, false));
        tool.pointerDown(pointer(20, 5, PointerButton.PRIMARY, false));

        boolean built = tool.buildPath();
        assertTrue(built);

        // Midway tile (11, 5) should have an intermediate height between 0 and 128
        TileSnapshot midSnap = world.tile(0, 11, 5).snapshot();
        int midHeight = (midSnap.southWestHeight() + midSnap.southEastHeight() + midSnap.northEastHeight() + midSnap.northWestHeight()) / 4;
        assertTrue(midHeight > 0 && midHeight < 128, "Midway height should be interpolated: " + midHeight);

        // Undo restores initial state
        session.undo();
        TileSnapshot undoSnap = world.tile(0, 11, 5).snapshot();
        int undoHeight = (undoSnap.southWestHeight() + undoSnap.southEastHeight() + undoSnap.northEastHeight() + undoSnap.northWestHeight()) / 4;
        assertEquals(0, undoHeight);
    }

    private static ToolContext context(EditorSession session) {
        return new ToolContext(session, new EmptyAssets(),
                (x, y) -> Optional.of(new WorldTile(0, (int) x, (int) y)));
    }

    private static PointerEvent pointer(float x, float y, PointerButton button, boolean alt) {
        return new PointerEvent(x, y, button, false, false, alt);
    }

    private static final class EmptyAssets implements com.rspsi.editor.assets.AssetRepository {
        @Override public List<com.rspsi.editor.assets.AssetDescriptor> search(String query) { return List.of(); }
        @Override public Optional<com.rspsi.editor.assets.AssetDescriptor> get(int id, String type) { return Optional.empty(); }
    }
}
