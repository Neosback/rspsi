package com.rspsi.editor.corpus;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WfcCorpusBuilderTest {
    @Test
    void capturesRealTileSocketsAndRadiusNeighborhoods() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        world.tile(0, 32, 32).restore(new TileSnapshot(
                0, 8, 16, 8, 4, 7, 3, 2, 0, List.of()));
        WorldRegion region = new WorldRegion(50, 50, world);

        WfcTrainingCorpus corpus = new WfcCorpusBuilder().build(List.of(region), 2);
        WfcTileState center = WfcTileState.from(world.tile(0, 32, 32).snapshot());

        assertEquals(4096L, corpus.frequencies().values().stream()
                .mapToLong(Long::longValue).sum());
        assertFalse(corpus.neighbors(center, -1, -1).isEmpty());
        assertEquals(2, corpus.radius());
        assertTrue(corpus.regionIds().contains(region.regionId()));
    }
}
