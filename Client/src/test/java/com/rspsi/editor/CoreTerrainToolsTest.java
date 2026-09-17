package com.rspsi.editor;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CoreTerrainToolsTest {
    @Test
    void overlayBrushStoresShapeAndRotationAsOneCommand() {
        WorldDocument world = new WorldDocument(2, 2);
        EditorSession session = new EditorSession(world);
        PaintOverlayTool tool = new PaintOverlayTool(9);
        tool.setShape(7);
        tool.setRotation(2);
        EditorToolController controller = new EditorToolController();
        controller.activate(tool, context(session));
        controller.pointerDown(pointer(0, 0));
        controller.pointerUp(pointer(0, 0));

        assertEquals(1, session.history().size());
        assertEquals(9, world.tile(0, 0, 0).snapshot().overlayId());
        assertEquals(7, world.tile(0, 0, 0).snapshot().overlayShape());
        assertEquals(2, world.tile(0, 0, 0).snapshot().overlayRotation());
        CompositeEditCommand overlayStroke = (CompositeEditCommand) session.history().commands().get(0);
        assertTrue(overlayStroke.changedTiles().contains(new TileCoordinate(0, 0, 0)));
        assertTrue(session.undo());
        assertEquals(0, world.tile(0, 0, 0).snapshot().overlayId());
    }

    @Test
    void heightAndFlagsBrushesUseTheSameHistoryPath() {
        WorldDocument world = new WorldDocument(2, 2);
        EditorSession session = new EditorSession(world);
        EditorToolController controller = new EditorToolController();

        controller.activate(new ChangeHeightTool(12), context(session));
        controller.pointerDown(pointer(1, 0));
        controller.pointerUp(pointer(1, 0));
        assertEquals(12, world.tile(0, 1, 0).snapshot().southWestHeight());

        controller.activate(new PaintFlagsTool(0x06), context(session));
        controller.pointerDown(pointer(1, 0));
        controller.pointerUp(pointer(1, 0));
        assertEquals(0x06, world.tile(0, 1, 0).snapshot().flags());
        assertEquals(2, session.history().size());
        assertTrue(session.history().commands().get(0) instanceof CompositeEditCommand);
        assertTrue(session.history().commands().get(1) instanceof CompositeEditCommand);
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
