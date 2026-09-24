package com.rspsi.editor.change;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangePlanTest {

    @Test
    void tracksWorldTilesAndAffectedRegionsDeterministically() {
        WorldTile west = new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10);
        WorldTile east = new WorldTile(0, 51 * 64, 50 * 64 + 10);
        TileSnapshot empty = snapshot(0);
        TileSnapshot westAfter = snapshot(7);
        TileSnapshot eastAfter = snapshot(8);

        ChangePlan plan = ChangePlan.builder("path")
                .setTile(west, empty, westAfter)
                .setTile(east, empty, eastAfter)
                .addDiagnostic("preview-ready")
                .build();

        assertEquals(List.of(west, east), List.copyOf(plan.tileChanges().keySet()));
        assertEquals(java.util.Set.of((50 << 8) | 50, (51 << 8) | 50),
                plan.affectedRegionIds());
        assertEquals(java.util.Set.of(west, east), plan.affectedTiles());
        assertEquals(java.util.Set.of(0), plan.affectedPlanes());
        assertEquals(new com.rspsi.editor.model.TileBounds(
                        west.x(), west.y(), east.x(), east.y()),
                plan.affectedWorldBounds().orElseThrow());
        assertEquals(List.of("preview-ready"), plan.diagnostics());
    }

    @Test
    void exactNoOpIsDroppedFromPlan() {
        WorldTile tile = new WorldTile(0, 3200, 3200);
        TileSnapshot state = snapshot(4);

        ChangePlan plan = ChangePlan.builder("noop")
                .setTile(tile, state, state)
                .build();

        assertTrue(plan.isEmpty());
        assertTrue(plan.affectedTiles().isEmpty());
        assertTrue(plan.affectedWorldBounds().isEmpty());
    }

    private static TileSnapshot snapshot(int underlay) {
        return new TileSnapshot(0, 0, 0, 0,
                underlay, 0, 0, 0, 0, List.of());
    }
}
