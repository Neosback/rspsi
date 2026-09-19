package com.rspsi.editor.knowledge;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.PaintOverlayCommand;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.Tile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.plugin.ContributionOwner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceTest {

    @Test
    void standardAnalyzersTagTilesDeterministically() {
        WorldDocument world = new WorldDocument(16, 16, 4);
        Tile bridgeTile = world.tile(1, 4, 4);
        bridgeTile.restore(new TileSnapshot(0, 0, 0, 0, 10, 0, 0, 0, OsrsTileFlags.BRIDGE, List.of()));

        Tile pathTile = world.tile(0, 5, 5);
        pathTile.restore(new TileSnapshot(0, 0, 0, 0, 10, 5, 0, 0, 0, List.of()));

        Tile cliffTile = world.tile(0, 6, 6);
        cliffTile.restore(new TileSnapshot(0, 200, 0, 0, 10, 0, 0, 0, 0, List.of()));

        Tile wallTile = world.tile(0, 7, 7);
        wallTile.restore(new TileSnapshot(0, 0, 0, 0, 10, 0, 0, 0, 0,
                List.of(new WorldObject(100, 0, 0, 0, 7, 7))));

        EditorSession session = new EditorSession(world);
        WorldKnowledgeService service = new WorldKnowledgeService(session);

        KnowledgeSnapshot snapshot = service.snapshot();

        assertTrue(snapshot.hasTag(new TileCoordinate(1, 4, 4), SemanticTag.BRIDGE));
        assertTrue(snapshot.hasTag(new TileCoordinate(0, 5, 5), SemanticTag.PATH));
        assertTrue(snapshot.hasTag(new TileCoordinate(0, 6, 6), SemanticTag.CLIFF));
        assertTrue(snapshot.hasTag(new TileCoordinate(0, 7, 7), SemanticTag.WALL));

        Set<TileCoordinate> bridgeTiles = snapshot.findTiles(SemanticTag.BRIDGE);
        assertEquals(1, bridgeTiles.size());
        assertTrue(bridgeTiles.contains(new TileCoordinate(1, 4, 4)));

        RegionProfile profile = snapshot.worldProfile();
        assertEquals(10, profile.dominantUnderlay().orElse(-1));
    }

    @Test
    void customAnalyzersCanBeRegisteredAndCleanedUp() {
        WorldDocument world = new WorldDocument(8, 8, 2);
        EditorSession session = new EditorSession(world);
        WorldKnowledgeService service = new WorldKnowledgeService(session);

        SemanticTag dungeonTag = SemanticTag.of("CUSTOM_DUNGEON");
        ContributionOwner owner = ContributionOwner.plugin("test-dungeon-plugin");

        AutoCloseable registration = service.registerAnalyzer(owner, (doc, emitter) -> {
            emitter.accept(new TileCoordinate(0, 2, 2), dungeonTag);
        });

        assertTrue(service.snapshot().hasTag(new TileCoordinate(0, 2, 2), dungeonTag));

        // Test unregistering
        try {
            registration.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertFalse(service.snapshot().hasTag(new TileCoordinate(0, 2, 2), dungeonTag));
    }

    @Test
    void sessionMutationsInvalidateSnapshotCache() {
        WorldDocument world = new WorldDocument(8, 8, 2);
        EditorSession session = new EditorSession(world);
        WorldKnowledgeService service = new WorldKnowledgeService(session);

        TileCoordinate target = new TileCoordinate(0, 3, 3);
        assertFalse(service.snapshot().hasTag(target, SemanticTag.PATH));

        // Mutate through command
        TileSnapshot before = world.tile(target).snapshot();
        TileSnapshot after = new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                before.underlayId(), 42, 0, 0,
                before.flags(), before.objects()
        );
        session.execute(new PaintOverlayCommand(target, before, after, "Paint overlay"));

        // Snapshot automatically invalidated and recalculated
        assertTrue(service.snapshot().hasTag(target, SemanticTag.PATH));
    }
}
