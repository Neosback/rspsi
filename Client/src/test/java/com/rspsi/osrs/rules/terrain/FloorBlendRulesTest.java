package com.rspsi.osrs.rules.terrain;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.RegionNeighborhood;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FloorBlendRulesTest {
    @Test
    void neighborhoodBlendSamplesAcrossEastRegionBorder() {
        WorldRegion west = new WorldRegion(10, 10, new WorldDocument(64, 64, 1));
        WorldRegion east = new WorldRegion(11, 10, new WorldDocument(64, 64, 1));
        west.document().tile(0, 63, 32).restore(tile(1));
        east.document().tile(0, 0, 32).restore(tile(2));

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 0
                        ? Optional.of(new FloorDefinitionView(0, -1, 0xAA0000, 16, 200, 80, 64, 256))
                        : id == 1
                        ? Optional.of(new FloorDefinitionView(1, -1, 0x00AA00, 48, 160, 120, 192, 256))
                        : Optional.empty();
            }
        };

        RegionNeighborhood neighborhood = new RegionNeighborhood(west,
                Map.of(west.regionId(), west, east.regionId(), east));
        int worldX = west.regionX() * 64 + 63;
        int worldY = west.regionY() * 64 + 32;

        int localOnly = FloorBlendRules.blendUnderlay(
                west.document(), definitions, 0, 63, 32);
        int crossRegion = FloorBlendRules.blendUnderlay(
                neighborhood, definitions, 0, worldX, worldY);

        assertNotEquals(localOnly, crossRegion);
        assertTrue(crossRegion >= 0);
    }

    private static TileSnapshot tile(int underlayId) {
        return new TileSnapshot(0, 0, 0, 0, underlayId, 0, 0, 0, 0, List.of());
    }
}
