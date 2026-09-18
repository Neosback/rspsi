package com.rspsi.editor.render;

import com.rspsi.editor.debug.DebugColor;
import com.rspsi.editor.debug.DiagnosticTileAnnotation;
import com.rspsi.editor.debug.UserTileMarker;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuneLiteRenderContractTest {
    @Test
    void sceneWindowRetainsRegionsAndInstanceContext() {
        WorldRegionWindow regions = new WorldRegionWindow(10, 12, 1, 1,
                Map.of(0x0A0C, new com.rspsi.editor.model.WorldRegion(10, 12,
                        new WorldDocument(64, 64, 4))));

        SceneWindow window = SceneWindow.from(regions);

        assertEquals(Set.of(0x0A0C), window.sourceRegionIds());
        assertEquals(4, window.planes());
        assertEquals(0, window.instanceTemplates().size());
        assertEquals(10 * 64, window.sceneBaseX());
    }

    @Test
    void terrainAndModelPacketsRejectDanglingIndices() {
        TileCoordinate tile = new TileCoordinate(0, 0, 0);
        TerrainRenderFace face = new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0);
        assertThrows(IllegalArgumentException.class, () -> new TerrainRenderPacket(
                tile,
                List.of(new TerrainRenderVertex(0, 0, 0, 0, 0, 0)),
                List.of(face), 0, 0, -1, 0, -1, true, false));

        ModelTriangle triangle = new ModelTriangle(0, 1, 2, 0, 0, 0, -1, 255, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new ModelRenderPacket(
                tile, 1, com.rspsi.editor.model.ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 0, 0, 0, 0, 0)),
                List.of(triangle), List.of(), -1, 0, 0, 0, 0, 0, 0, false, false));
    }

    @Test
    void persistedMarkersAndDiagnosticsAreDistinctModels() {
        TileCoordinate tile = new TileCoordinate(0, 3, 4);
        UserTileMarker marker = new UserTileMarker("marker-1", tile, "Entrance", DebugColor.YELLOW, true);
        DiagnosticTileAnnotation diagnostic = new DiagnosticTileAnnotation(
                tile, "bridge", "Effective plane 0", DebugColor.CYAN);

        assertEquals("Entrance", marker.label());
        assertEquals("bridge", diagnostic.kind());
        assertEquals(50, DebugColor.MARKER_FILL.alpha());
    }
}
