package com.rspsi.editor;

import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.viewport.SurfaceHit;
import com.rspsi.editor.viewport.Viewport;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceHitTest {

    @Test
    void terrainHitContainsOnlySurfaceTile() {
        WorldTile tile = new WorldTile(2, 3200, 3201);
        SurfaceHit hit = SurfaceHit.terrain(tile);

        assertEquals(tile, hit.tile());
        assertEquals(tile, hit.targetTile());
        assertEquals(-1, hit.objectId());
        assertFalse(hit.objectHit());
        assertTrue(hit.objectAnchor().isEmpty());
        assertTrue(hit.object().isEmpty());
    }

    @Test
    void objectHitCarriesAnchorAndResolvedPlacement() {
        WorldTile rayTile = new WorldTile(0, 3205, 3206);
        WorldTile anchor = new WorldTile(0, 3204, 3205);
        WorldObject placement = new WorldObject(1276, 10, 2, 0, 4, 5);

        SurfaceHit hit = SurfaceHit.object(rayTile, 1276, anchor, placement);

        assertTrue(hit.objectHit());
        assertEquals(1276, hit.objectId());
        assertEquals(rayTile, hit.tile());
        assertEquals(anchor, hit.targetTile());
        assertEquals(Optional.of(anchor), hit.objectAnchor());
        assertEquals(Optional.of(placement), hit.object());
    }

    @Test
    void unresolvedObjectHitStillPreservesSemanticIdentity() {
        WorldTile tile = new WorldTile(0, 3210, 3211);
        SurfaceHit hit = SurfaceHit.object(tile, 99, tile, null);

        assertTrue(hit.objectHit());
        assertEquals(99, hit.objectId());
        assertEquals(tile, hit.targetTile());
        assertTrue(hit.object().isEmpty());
    }

    @Test
    void legacyViewportHitAdapterPreservesPreciseObjectAtResult() {
        WorldTile tile = new WorldTile(0, 3200, 3200);
        WorldObject placement = new WorldObject(55, 10, 1, 0, 3, 4);
        Viewport viewport = new Viewport() {
            @Override
            public Optional<WorldTile> tileAt(float x, float y) {
                return Optional.of(tile);
            }

            @Override
            public Optional<WorldObject> objectAt(float x, float y) {
                return Optional.of(placement);
            }
        };

        SurfaceHit hit = viewport.hitAt(10.0f, 20.0f).orElseThrow();

        assertTrue(hit.objectHit());
        assertEquals(placement.id(), hit.objectId());
        assertEquals(placement, hit.object().orElseThrow());
        assertEquals(tile, hit.targetTile());
    }

    @Test
    void rejectsMismatchedObjectPlacement() {
        WorldTile tile = new WorldTile(0, 3200, 3200);
        WorldObject wrong = new WorldObject(100, 10, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> SurfaceHit.object(tile, 99, tile, wrong));
    }

    @Test
    void rejectsObjectAnchorOnAnotherPlane() {
        WorldTile tile = new WorldTile(0, 3200, 3200);
        WorldTile wrongPlane = new WorldTile(1, 3200, 3200);

        assertThrows(IllegalArgumentException.class,
                () -> SurfaceHit.object(tile, 99, wrongPlane, null));
    }
}
