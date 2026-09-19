package com.rspsi.osrs.rules.loc;

import com.rspsi.editor.model.ObjectCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocShapeCatalogTest {

    @Test
    void containsAll23Shapes() {
        List<LocShapeCatalog.LocShapeDescriptor> all = LocShapeCatalog.all();
        assertEquals(23, all.size());

        for (int id = 0; id <= 22; id++) {
            Optional<LocShapeCatalog.LocShapeDescriptor> desc = LocShapeCatalog.get(id);
            assertTrue(desc.isPresent(), "Shape " + id + " must be present in catalog");
            assertEquals(id, desc.get().id());
        }

        assertFalse(LocShapeCatalog.get(-1).isPresent());
        assertFalse(LocShapeCatalog.get(23).isPresent());
    }

    @Test
    void wallShapesClassification() {
        for (int id = 0; id <= 3; id++) {
            LocShapeCatalog.LocShapeDescriptor desc = LocShapeCatalog.get(id).orElseThrow();
            assertEquals(ObjectCategory.WALL, desc.category());
            assertTrue(desc.isWall());
            assertFalse(desc.isWallDecor());
            assertFalse(desc.isGround());
            assertFalse(desc.isRoof());
        }

        // L-shaped wall (type 2) has 2 model variants (wall orientation A and B)
        assertEquals(2, LocShapeCatalog.get(2).orElseThrow().variantCount());
        assertEquals(1, LocShapeCatalog.get(0).orElseThrow().variantCount());
    }

    @Test
    void wallDecorShapesClassification() {
        for (int id = 4; id <= 8; id++) {
            LocShapeCatalog.LocShapeDescriptor desc = LocShapeCatalog.get(id).orElseThrow();
            assertEquals(ObjectCategory.WALL_DECOR, desc.category());
            assertTrue(desc.isWallDecor());
            assertFalse(desc.isWall());
        }

        // Decor types 5, 6, 8 require wall displacement
        assertTrue(LocShapeCatalog.get(5).orElseThrow().hasDisplacement());
        assertTrue(LocShapeCatalog.get(6).orElseThrow().hasDisplacement());
        assertTrue(LocShapeCatalog.get(8).orElseThrow().hasDisplacement());
        assertFalse(LocShapeCatalog.get(4).orElseThrow().hasDisplacement());
        assertFalse(LocShapeCatalog.get(7).orElseThrow().hasDisplacement());
    }

    @Test
    void roofShapesClassification() {
        for (int id = 12; id <= 21; id++) {
            LocShapeCatalog.LocShapeDescriptor desc = LocShapeCatalog.get(id).orElseThrow();
            assertTrue(desc.isRoof(), "Shape " + id + " must be marked as roof");
        }
        assertFalse(LocShapeCatalog.get(10).orElseThrow().isRoof());
        assertFalse(LocShapeCatalog.get(22).orElseThrow().isRoof());
    }

    @Test
    void groundDecorClassification() {
        LocShapeCatalog.LocShapeDescriptor desc = LocShapeCatalog.get(22).orElseThrow();
        assertEquals(ObjectCategory.GROUND_DECOR, desc.category());
        assertTrue(desc.isGroundDecor());
        assertFalse(desc.isWall());
        assertFalse(desc.isWallDecor());
    }
}
