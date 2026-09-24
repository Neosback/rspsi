package com.rspsi.api.model;

import com.rspsi.api.AABB;
import com.rspsi.api.Model;
import com.rspsi.api.ModelData;
import com.rspsi.cache.definition.ModelGeometryView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecodedModelDataTest {
    /** One flat triangle in the XZ plane, wound so its normal points up (-Y). */
    private static ModelGeometryView flatTriangle(int color) {
        return new ModelGeometryView(1,
                new int[]{0, 0, 0, 0, 0, 100, 100, 0, 0},
                new int[]{0, 1, 2}, new short[]{(short) color}, new int[]{0}, new int[]{-1});
    }

    @Test
    void rotationsMatchTheClient() {
        ModelData data = DecodedModelData.of(new ModelGeometryView(1,
                new int[]{10, -5, 20}, new int[0], new short[0], new int[0], new int[0]));

        data.rotateY90Ccw();
        assertEquals(20, data.getVerticesX()[0]);
        assertEquals(-10, data.getVerticesZ()[0]);
        data.rotateY270Ccw();
        assertEquals(10, data.getVerticesX()[0]);
        assertEquals(20, data.getVerticesZ()[0]);
        data.rotateY180Ccw().translate(1, 2, 3).scale(256, 128, 64);
        assertEquals((-10 + 1) * 256 / 128, data.getVerticesX()[0]);
        assertEquals(-3, data.getVerticesY()[0]);
        assertEquals((-20 + 3) * 64 / 128, data.getVerticesZ()[0]);
    }

    @Test
    void loadedCopiesAreIndependentAndRecolorOnlyTouchesTheCopy() {
        ModelGeometryView geometry = flatTriangle(1000);
        ModelData first = DecodedModelData.of(geometry);
        ModelData second = DecodedModelData.of(geometry);

        first.recolor((short) 1000, (short) 2000).rotateY90Ccw();

        assertEquals(2000, first.getFaceColors()[0]);
        assertEquals(1000, second.getFaceColors()[0]);
        assertEquals(0, second.getVerticesX()[1]);
        assertNotSame(first.getVerticesX(), second.getVerticesX());
        ModelData shallow = first.shallowCopy();
        assertTrue(shallow.getVerticesX() == first.getVerticesX(), "shallow copies share arrays");
        assertNotSame(first.getVerticesX(), shallow.cloneVertices().getVerticesX());
    }

    @Test
    void lightingFollowsModelDataToModel() {
        // hue/sat bits 0x2A80 with lightness 100.
        int hsl = 0x2A80 | 100;
        Model model = DecodedModelData.of(flatTriangle(hsl)).light();

        // Normal: cross((0,0,100), (100,0,0)) = (0, 10000, 0) -> scaled to (0, 256, 0), magnitude 1.
        int lightMagnitude = (int) Math.sqrt(50 * 50 + 10 * 10 + 50 * 50);
        int intensity = lightMagnitude * 768 >> 8;
        int light = (-10 * 256) / intensity + 64;
        int expected = (hsl & 0xFF80) + Math.max(2, Math.min(126, (hsl & 127) * light >> 7));
        assertEquals(expected, model.getFaceColors1()[0]);
        assertEquals(expected, model.getFaceColors2()[0]);
        assertEquals(expected, model.getFaceColors3()[0]);
        assertArrayEquals(new short[]{(short) hsl}, model.getUnlitFaceColors());
    }

    @Test
    void hiddenAndFlatFacesUseTheClientMarkers() {
        ModelGeometryView hidden = new ModelGeometryView(1,
                new int[]{0, 0, 0, 0, 0, 100, 100, 0, 0}, new int[]{0, 1, 2},
                new short[]{(short) 500}, new int[]{255}, new int[]{-1});
        assertEquals(-2, DecodedModelData.of(hidden).light().getFaceColors3()[0], "alpha 255 hides the face");
    }

    @Test
    void boundsAndAabbComeFromTheClientCalculation() {
        Model model = DecodedModelData.of(new ModelGeometryView(1,
                new int[]{-64, 0, -64, 64, -200, 64, 64, 10, -64},
                new int[]{0, 1, 2}, new short[]{(short) 100}, new int[]{0}, new int[]{-1})).light();

        assertEquals(200, model.getModelHeight());
        assertEquals(10, model.getBottomY());
        AABB box = model.getAABB(0);
        assertEquals(0, box.getCenterX());
        assertEquals(64, box.getExtremeX());
        assertTrue(model.getRadius() >= model.getXYZMag());
    }
}
