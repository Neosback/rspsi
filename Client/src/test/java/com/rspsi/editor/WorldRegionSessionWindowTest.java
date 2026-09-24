package com.rspsi.editor;

import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.change.ChangePlanValidation;
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

    @Test
    void commitsCrossRegionChangePlanAsOneWindowHistoryEntry() {
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
        WorldRegionSessionWindow sessions = new WorldRegionSessionWindow(window, Map.of(
                west.regionId(), westSession,
                east.regionId(), eastSession));

        WorldTile westTile = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile eastTile = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot westBefore = westDocument.tile(0, 63, 10).snapshot();
        TileSnapshot eastBefore = eastDocument.tile(0, 0, 10).snapshot();
        TileSnapshot westAfter = withUnderlay(westBefore, 7);
        TileSnapshot eastAfter = withUnderlay(eastBefore, 8);

        ChangePlan plan = ChangePlan.builder("Cross-region path")
                .setTile(westTile, westBefore, westAfter)
                .setTile(eastTile, eastBefore, eastAfter)
                .build();

        assertTrue(sessions.commit(plan));

        assertEquals(7, westDocument.tile(0, 63, 10).snapshot().underlayId());
        assertEquals(8, eastDocument.tile(0, 0, 10).snapshot().underlayId());
        assertEquals(1, sessions.changeHistory().size());
        assertEquals(1, sessions.changeHistory().position());
        assertEquals(1, westSession.history().position());
        assertEquals(1, eastSession.history().position());
        assertEquals(java.util.Set.of(west.regionId(), east.regionId()),
                sessions.dirtyRegionIds());

        assertTrue(sessions.undoChangePlan());
        assertEquals(westBefore, westDocument.tile(0, 63, 10).snapshot());
        assertEquals(eastBefore, eastDocument.tile(0, 0, 10).snapshot());
        assertEquals(0, sessions.changeHistory().position());
        assertFalse(sessions.isDirty());

        assertTrue(sessions.redoChangePlan());
        assertEquals(westAfter, westDocument.tile(0, 63, 10).snapshot());
        assertEquals(eastAfter, eastDocument.tile(0, 0, 10).snapshot());
        assertEquals(1, sessions.changeHistory().position());
        assertTrue(sessions.isDirty());
    }

    @Test
    void staleChangePlanFailsBeforeAnyRegionMutates() {
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

        WorldTile westTile = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile eastTile = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot westBefore = westDocument.tile(0, 63, 10).snapshot();
        TileSnapshot eastBefore = eastDocument.tile(0, 0, 10).snapshot();

        ChangePlan plan = ChangePlan.builder("stale")
                .setTile(westTile, westBefore, withUnderlay(westBefore, 7))
                .setTile(eastTile, eastBefore, withUnderlay(eastBefore, 8))
                .build();

        eastDocument.tile(0, 0, 10).restore(withUnderlay(eastBefore, 99));

        ChangePlanValidation validation = sessions.validate(plan);
        assertFalse(validation.canCommit());
        assertEquals(1, validation.conflicts().size());
        assertEquals(ChangePlanValidation.ConflictCode.STALE_SOURCE,
                validation.conflicts().get(0).code());
        assertEquals(eastTile, validation.conflicts().get(0).tile());

        assertThrows(IllegalStateException.class, () -> sessions.commit(plan));
        assertEquals(westBefore, westDocument.tile(0, 63, 10).snapshot(),
                "preflight must reject the whole plan before mutating the first region");
        assertEquals(0, westSession.history().position());
        assertEquals(0, eastSession.history().position());
        assertEquals(0, sessions.changeHistory().size());
    }

    @Test
    void missingRegionChangePlanFailsBeforeLoadedRegionMutates() {
        WorldDocument westDocument = new WorldDocument(64, 64, 1);
        WorldRegion west = new WorldRegion(50, 50, westDocument);
        WorldRegionWindow window =
                new WorldRegionWindow(50, 50, 2, 1, Map.of(west.regionId(), west));
        EditorSession westSession = new EditorSession(westDocument, west.window());
        WorldRegionSessionWindow sessions =
                new WorldRegionSessionWindow(window, Map.of(west.regionId(), westSession));

        WorldTile westTile = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile missingTile = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot westBefore = westDocument.tile(0, 63, 10).snapshot();
        TileSnapshot placeholder = new WorldDocument(64, 64, 1)
                .tile(0, 0, 10).snapshot();

        ChangePlan plan = ChangePlan.builder("missing neighbor")
                .setTile(westTile, westBefore, withUnderlay(westBefore, 7))
                .setTile(missingTile, placeholder, withUnderlay(placeholder, 8))
                .build();

        ChangePlanValidation validation = sessions.validate(plan);
        assertFalse(validation.canCommit());
        assertEquals(1, validation.conflicts().size());
        assertEquals(ChangePlanValidation.ConflictCode.UNLOADED_REGION,
                validation.conflicts().get(0).code());
        assertEquals(missingTile, validation.conflicts().get(0).tile());

        assertThrows(IllegalStateException.class, () -> sessions.commit(plan));
        assertEquals(westBefore, westDocument.tile(0, 63, 10).snapshot());
        assertEquals(0, westSession.history().position());
    }

    @Test
    void localHistoryDivergenceBlocksWindowUndoWithoutPartialMutation() {
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

        WorldTile westTile = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile eastTile = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot westBefore = westDocument.tile(0, 63, 10).snapshot();
        TileSnapshot eastBefore = eastDocument.tile(0, 0, 10).snapshot();
        TileSnapshot westAfter = withUnderlay(westBefore, 7);
        TileSnapshot eastAfter = withUnderlay(eastBefore, 8);
        sessions.commit(ChangePlan.builder("cross")
                .setTile(westTile, westBefore, westAfter)
                .setTile(eastTile, eastBefore, eastAfter)
                .build());

        TileCoordinate extra = new TileCoordinate(0, 1, 1);
        TileSnapshot extraBefore = westDocument.tile(extra).snapshot();
        westSession.execute(new SetTileCommand(
                extra, extraBefore, withUnderlay(extraBefore, 22), "local edit"));

        assertThrows(IllegalStateException.class, sessions::undoChangePlan);
        assertEquals(westAfter, westDocument.tile(0, 63, 10).snapshot());
        assertEquals(eastAfter, eastDocument.tile(0, 0, 10).snapshot(),
                "preflight must prevent another region from undoing first");
        assertEquals(1, sessions.changeHistory().position());
    }

    @Test
    void listenerFailureRollsBackEveryRegionIncludingTheFailingSession() {
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

        WorldTile westTile = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile eastTile = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot westBefore = westDocument.tile(0, 63, 10).snapshot();
        TileSnapshot eastBefore = eastDocument.tile(0, 0, 10).snapshot();

        java.util.concurrent.atomic.AtomicBoolean failOnce =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        eastSession.addChangeListener(ignored -> {
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("listener failure");
            }
        });

        ChangePlan plan = ChangePlan.builder("atomic failure")
                .setTile(westTile, westBefore, withUnderlay(westBefore, 7))
                .setTile(eastTile, eastBefore, withUnderlay(eastBefore, 8))
                .build();

        assertThrows(IllegalStateException.class, () -> sessions.commit(plan));
        assertEquals(westBefore, westDocument.tile(0, 63, 10).snapshot());
        assertEquals(eastBefore, eastDocument.tile(0, 0, 10).snapshot());
        assertEquals(0, westSession.history().position());
        assertEquals(0, eastSession.history().position());
        assertEquals(0, sessions.changeHistory().size());
    }

    private static TileSnapshot withUnderlay(TileSnapshot source, int underlay) {
        return new TileSnapshot(
                source.southWestHeight(), source.southEastHeight(),
                source.northEastHeight(), source.northWestHeight(),
                underlay, source.overlayId(), source.overlayShape(),
                source.overlayRotation(), source.flags(), source.objects());
    }

}
