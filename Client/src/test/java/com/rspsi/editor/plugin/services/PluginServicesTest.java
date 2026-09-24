package com.rspsi.editor.plugin.services;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.event.SelectionChangedEvent;
import com.rspsi.editor.plugin.event.TileEditedEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PluginServicesTest {
    @Test
    void commandBackedTerrainAndSelectionServicesPublishEvents() throws Exception {
        EditorSession session = new EditorSession(new WorldDocument(4, 4, 1));
        PluginServices services = PluginServices.resolve(
                session, AssetRepository.empty(), new EditorPluginRegistry());
        AtomicInteger tileEvents = new AtomicInteger();
        AtomicInteger selectionEvents = new AtomicInteger();
        AutoCloseable tileSub = services.events().subscribe(
                TileEditedEvent.class, ignored -> tileEvents.incrementAndGet());
        AutoCloseable selectionSub = services.events().subscribe(
                SelectionChangedEvent.class, ignored -> selectionEvents.incrementAndGet());

        services.terrain().setVertexHeight(0, 1, 1, 96);
        services.selections().select(Set.of(new TileCoordinate(0, 2, 2)));

        assertEquals(96, services.terrain().vertexHeight(0, 1, 1));
        assertEquals(1, tileEvents.get());
        assertEquals(1, selectionEvents.get());
        assertTrue(session.history().canUndo());

        tileSub.close();
        selectionSub.close();
    }

    @Test
    void releaseClosesHostOwnedExecutionService() {
        EditorSession session = new EditorSession(new WorldDocument(4, 4, 1));
        EditorPluginRegistry registry = new EditorPluginRegistry();
        PluginServices services = PluginServices.resolve(
                session, AssetRepository.empty(), registry);

        PluginServices.release(registry);

        assertTrue(services.execution().isClosed());
    }

}
