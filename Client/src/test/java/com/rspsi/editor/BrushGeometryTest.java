package com.rspsi.editor;

import com.rspsi.editor.brush.HeightBrushContext;
import com.rspsi.editor.brush.builtin.CheckerBrush;
import com.rspsi.editor.brush.builtin.CircleBrush;
import com.rspsi.editor.brush.builtin.DiamondBrush;
import com.rspsi.editor.brush.builtin.GaussianBrush;
import com.rspsi.editor.brush.builtin.SlopeBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.brush.builtin.TerraceBrush;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushGeometryTest {

    @Test
    void spatialBrushesHaveExpectedFootprints() {
        SquareBrush square = new SquareBrush();
        CircleBrush circle = new CircleBrush();
        DiamondBrush diamond = new DiamondBrush();
        CheckerBrush checker = new CheckerBrush();

        assertTrue(square.contains(2, 2, 2));
        assertFalse(square.contains(3, 0, 2));

        assertTrue(circle.contains(2, 0, 2));
        assertFalse(circle.contains(2, 2, 2));

        assertTrue(diamond.contains(1, 1, 2));
        assertFalse(diamond.contains(2, 1, 2));

        assertTrue(checker.contains(1, 1, 2));
        assertFalse(checker.contains(1, 0, 2));
    }

    @Test
    void gaussianIsWeightedAndBounded() {
        GaussianBrush gaussian = new GaussianBrush();
        assertEquals(1.0, gaussian.weight(0, 0, 3), 0.000001);
        double near = gaussian.weight(1, 0, 3);
        double edge = gaussian.weight(3, 0, 3);
        assertTrue(near > edge);
        assertTrue(edge > 0.0);
        assertEquals(0.0, gaussian.weight(4, 0, 3), 0.0);
    }

    @Test
    void heightBrushesEvaluateDeterministically() {
        SlopeBrush slope = new SlopeBrush();
        HeightBrushContext slopeContext = new HeightBrushContext(2, 0, 2, 0.0, 64, 16);
        assertEquals(164, slope.evaluateHeight(100, 100, 1.0, slopeContext));

        TerraceBrush terrace = new TerraceBrush();
        HeightBrushContext terraceContext = new HeightBrushContext(0, 0, 2, 0.0, 0, 16);
        assertEquals(96, terrace.evaluateHeight(101, 100, 1.0, terraceContext));
    }
}
