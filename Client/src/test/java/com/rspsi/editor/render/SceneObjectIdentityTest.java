package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneObjectIdentityTest {
    @Test
    void rebuildsProduceTheSameSemanticIdentity() {
        WorldObject object = new WorldObject(1327, 10, 2, 1, 12, 34);

        SceneObjectIdentity first = SceneObjectIdentity.of(object, 2, 3);
        SceneObjectIdentity second = SceneObjectIdentity.of(
                new WorldObject(1327, 10, 2, 1, 12, 34), 2, 3);

        assertEquals(first, second);
        assertEquals(first.stableId(), second.stableId());
    }

    @Test
    void placementChangesProduceDifferentIdentity() {
        SceneObjectIdentity original =
                SceneObjectIdentity.of(new WorldObject(1327, 10, 0, 0, 5, 6), 2, 3);
        SceneObjectIdentity moved =
                SceneObjectIdentity.of(new WorldObject(1327, 10, 0, 0, 6, 6), 2, 3);
        SceneObjectIdentity rotated =
                SceneObjectIdentity.of(new WorldObject(1327, 10, 1, 0, 5, 6), 3, 2);

        assertNotEquals(original, moved);
        assertNotEquals(original.stableId(), moved.stableId());
        assertNotEquals(original, rotated);
        assertNotEquals(original.stableId(), rotated.stableId());
    }

    @Test
    void anchorRebasePreservesAuthoredPlacementSemantics() {
        SceneObjectIdentity local =
                SceneObjectIdentity.of(new WorldObject(42, 10, 1, 0, 5, 6), 3, 2);

        SceneObjectIdentity world = local.withAnchor(new TileCoordinate(0, 3205, 6406));

        assertTrue(world.present());
        assertEquals(local.objectId(), world.objectId());
        assertEquals(local.category(), world.category());
        assertEquals(local.shape(), world.shape());
        assertEquals(local.rotation(), world.rotation());
        assertEquals(local.authoredPlane(), world.authoredPlane());
        assertEquals(local.footprintWidth(), world.footprintWidth());
        assertEquals(local.footprintLength(), world.footprintLength());
        assertEquals(3205, world.anchorX());
        assertEquals(6406, world.anchorY());
        assertNotEquals(local.stableId(), world.stableId());
    }
}
