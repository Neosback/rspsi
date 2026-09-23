package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ZoneVboManagerTest {

    @Test
    void tilesInSameEightByEightZoneShareZoneKey() {
        // WorldTileAddress.of(worldX, worldY, plane)
        WorldTileAddress origin = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress inside = WorldTileAddress.of(3207, 3207, 0);
        assertEquals(ZoneVboManager.zoneKey(origin), ZoneVboManager.zoneKey(inside));
    }

    @Test
    void crossingEightTileBoundaryProducesDistinctZoneKeys() {
        WorldTileAddress zoneA = WorldTileAddress.of(3207, 3200, 0);
        WorldTileAddress zoneB = WorldTileAddress.of(3208, 3200, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneA), ZoneVboManager.zoneKey(zoneB));

        WorldTileAddress zoneC = WorldTileAddress.of(3200, 3207, 0);
        WorldTileAddress zoneD = WorldTileAddress.of(3200, 3208, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneC), ZoneVboManager.zoneKey(zoneD));
    }

    @Test
    void differentPlanesProduceDistinctZoneKeys() {
        WorldTileAddress plane0 = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress plane1 = WorldTileAddress.of(3200, 3200, 1);
        assertNotEquals(ZoneVboManager.zoneKey(plane0), ZoneVboManager.zoneKey(plane1));
    }
}
