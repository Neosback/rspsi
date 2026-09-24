package com.rspsi.editor;

import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorldRegionSessionWindowTest {

    @Test
    void resolvesWorldTilesAcrossRegionBoundaryWithoutMergedAuthoringDocument() {
        WorldDocument westDocument = new WorldDocument(64, 64, 1);
        WorldDocument eastDocument = new WorldDocument(64, 64, 1);
        WorldRegion west = new WorldRegion(50, 50, westDocument);
        WorldRegion east = new WorldRegion(51, 50, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(50, 50, 2, 1, Map.of(
                west.regionId(), west,
                east.regionId(), east));

        EditorSession westSession = new EditorSession(westDocument, west.window());
        EditorSession eastSession = new EditorSession(eastDocument, east.window());
        WorldRegionSessionWindow sessions = new WorldRegionSessionWindow(window, Map.of(
                west.regionId(), westSession,
                east.regionId(), eastSession));

        WorldRegionSessionWindow.ResolvedTile westEdge =
                sessions.resolve(new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10)).orElseThrow();
        WorldRegionSessionWindow.ResolvedTile eastEdge =
                sessions.resolve(new WorldTile(0, 51 * 64, 50 * 64 + 10)).orElseThrow();

        assertSame(westSession, westEdge.session());
        assertEquals(new LocalTile(0, 63, 10), westEdge.localTile());
        assertSame(eastSession, eastEdge.session());
        assertEquals(new LocalTile(0, 0, 10), eastEdge.localTile());
        assertSame(westDocument, window.regions().get(west.regionId()).document());
        assertSame(eastDocument, window.regions().get(east.regionId()).document());
    }

    @Test
    void missingRegionsRemainUnresolvedAndExplicit() {
        WorldRegion loaded = new WorldRegion(50, 50, new WorldDocument(64, 64, 1));
        WorldRegionWindow window = new WorldRegionWindow(
                50, 50, 2, 1, Map.of(loaded.regionId(), loaded));
        EditorSession session = new EditorSession(loaded.document(), loaded.window());
        WorldRegionSessionWindow sessions = new WorldRegionSessionWindow(
                window, Map.of(loaded.regionId(), session));

        assertTrue(sessions.resolve(new WorldTile(0, 50 * 64, 50 * 64)).isPresent());
        assertTrue(sessions.resolve(new WorldTile(0, 51 * 64, 50 * 64)).isEmpty());
        assertEquals(java.util.Set.of((51 << 8) | 50), sessions.missingRegionIds());
    }

    @Test
    void dirtyRegionTrackingIgnoresCleanNeighborAndSaveUsesOneWindowBoundary() {
        WorldDocument westDocument = new WorldDocument(64, 64, 1);
        WorldDocument eastDocument = new WorldDocument(64, 64, 1);
        WorldRegion west = new WorldRegion(50, 50, westDocument);
        WorldRegion east = new WorldRegion(51, 50, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(50, 50, 2, 1, Map.of(
                west.regionId(), west,
                east.regionId(), east));

        EditorSession westSession = new EditorSession(westDocument, west.window());
        EditorSession eastSession = new EditorSession(eastDocument, east.window());
        westSession.markSaved();
        eastSession.markSaved();

        AtomicInteger saves = new AtomicInteger();
        WorldRegionSessionWindow sessions = new WorldRegionSessionWindow(
                window,
                Map.of(west.regionId(), westSession, east.regionId(), eastSession),
                current -> {
                    saves.incrementAndGet();
                    for (int regionId : current.dirtyRegionIds()) {
                        current.session(regionId).orElseThrow().markSaved();
                    }
                });

        TileCoordinate tile = new TileCoordinate(0, 63, 10);
        TileSnapshot before = westDocument.tile(tile).snapshot();
        TileSnapshot after = new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                17, before.overlayId(), before.overlayShape(),
                before.overlayRotation(), before.flags(), before.objects());
        westSession.execute(new SetTileCommand(tile, before, after, "paint edge"));

        assertEquals(java.util.Set.of(west.regionId()), sessions.dirtyRegionIds());
        assertTrue(sessions.isDirty());
        sessions.save();

        assertEquals(1, saves.get());
        assertFalse(sessions.isDirty());
        assertFalse(westSession.isSessionSaveDirty());
        assertFalse(eastSession.isSessionSaveDirty());

        sessions.save();
        assertEquals(1, saves.get(), "clean multi-region save should be a no-op");
    }

    @Test
    void rejectsSessionWhoseDocumentIsNotTheCanonicalRegionDocument() {
        WorldRegion region = new WorldRegion(50, 50, new WorldDocument(64, 64, 1));
        WorldRegionWindow window =
                new WorldRegionWindow(50, 50, 1, 1, Map.of(region.regionId(), region));
        EditorSession different = new EditorSession(
                new WorldDocument(64, 64, 1), region.window());

        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegionSessionWindow(
                        window, Map.of(region.regionId(), different)));
    }

    @Test
    void readOnlyRegionReportsWorldTileAsNotEditable() {
        WorldRegion region = new WorldRegion(50, 50, new WorldDocument(64, 64, 1));
        WorldRegionWindow window =
                new WorldRegionWindow(50, 50, 1, 1, Map.of(region.regionId(), region));
        EditorSession readOnly = EditorSession.readOnly(region.document(), region.window());
        WorldRegionSessionWindow sessions =
                new WorldRegionSessionWindow(window, Map.of(region.regionId(), readOnly));

        assertFalse(sessions.canEdit(new WorldTile(0, 50 * 64 + 1, 50 * 64 + 1)));
        assertFalse(sessions.canSave());
    }
}
