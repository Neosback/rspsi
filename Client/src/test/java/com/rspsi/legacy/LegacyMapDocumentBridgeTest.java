package com.rspsi.legacy;

import com.jagex.map.MapRegion;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMapDocumentBridgeTest {

    @Test
    void importsTerrainFieldsAcrossAllPlanes() {
        MapRegion region = new MapRegion(null, 2, 2);
        region.tileHeights[2][1][1] = -30;
        region.tileHeights[2][2][1] = -20;
        region.tileHeights[2][2][2] = -10;
        region.tileHeights[2][1][2] = -25;
        region.underlays[2][1][1] = 7;
        region.overlays[2][1][1] = 9;
        region.overlayShapes[2][1][1] = 4;
        region.overlayOrientations[2][1][1] = 3;
        region.tileFlags[2][1][1] = 6;

        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        TileSnapshot tile = document.tile(2, 1, 1).snapshot();

        assertEquals(new TileSnapshot(-30, -20, -10, -25, 7, 9, 4, 3, 6, java.util.List.of()), tile);
    }

    @Test
    void appliesOnlyChangedUnderlaysAndRefreshesOncePerCommand() {
        MapRegion region = new MapRegion(null, 2, 2);
        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        EditorSession session = new EditorSession(document);
        AtomicInteger refreshes = new AtomicInteger();
        LegacyMapDocumentBridge bridge = new LegacyMapDocumentBridge(region, null, refreshes::incrementAndGet);
        bridge.attach(session);

        TileCoordinate coordinate = new TileCoordinate(3, 1, 1);
        TileSnapshot before = document.tile(coordinate).snapshot();
        TileSnapshot after = new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(), 12, before.overlayId(),
                before.overlayShape(), before.overlayRotation(), before.flags(), before.objects());

        session.execute(new SetTileCommand(coordinate, before, after, "paint"));
        assertEquals(12, region.underlays[3][1][1]);
        assertEquals(1, refreshes.get());

        session.undo();
        assertEquals(0, region.underlays[3][1][1]);
        assertEquals(2, refreshes.get());

        session.redo();
        assertEquals(12, region.underlays[3][1][1]);
        assertEquals(3, refreshes.get());
        bridge.close();
    }

    @Test
    void invalidChangedTilesAreIgnoredWithoutRefresh() {
        MapRegion region = new MapRegion(null, 2, 2);
        WorldDocument document = LegacyMapDocumentBridge.importTerrain(region);
        AtomicInteger refreshes = new AtomicInteger();
        LegacyMapDocumentBridge bridge = new LegacyMapDocumentBridge(region, null, refreshes::incrementAndGet);
        EditorSession session = new EditorSession(document);
        bridge.attach(session);

        bridge.changed(java.util.Set.of(new TileCoordinate(0, 99, 0)));
        assertEquals(0, refreshes.get());
    }
}
