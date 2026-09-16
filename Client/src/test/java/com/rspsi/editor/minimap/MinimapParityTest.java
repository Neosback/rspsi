package com.rspsi.editor.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapParityTest {
    @Test
    void identicalRastersMatch() {
        MinimapImage image = new MinimapImage(0, 2, 2,
                new int[]{0xFF102030, 0xFF102030, 0xFF203040, 0xFF203040});

        MinimapParity.Report report = MinimapParity.compare(image,
                new MinimapImage(0, 2, 2, image.argb()));

        assertTrue(report.matches());
        assertEquals(0, report.differingPixels());
    }

    @Test
    void reportsPixelAndDimensionDifferences() {
        MinimapImage expected = new MinimapImage(0, 2, 2, new int[4]);
        MinimapImage actual = new MinimapImage(0, 2, 3,
                new int[]{0, 0xFFFFFFFF, 0, 0, 0, 0});

        MinimapParity.Report report = MinimapParity.compare(expected, actual);

        assertFalse(report.matches());
        assertEquals(2, report.differingPixels());
        assertEquals(1, report.differences().size());
        assertEquals(1, report.differences().get(0).x());
        assertEquals(0, report.differences().get(0).y());
    }
}
