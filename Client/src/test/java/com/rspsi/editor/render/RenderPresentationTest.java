package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPresentationTest {
    @Test
    void exposureIsPresentationOnlyAndUsesPowerOfTwoStops() {
        RenderPresentation presentation = new RenderPresentation(1.0, 1.0);

        assertEquals(200, presentation.apply(100));
        assertEquals(255, presentation.apply(200));
        assertEquals(100, RenderPresentation.neutral().apply(100));
        assertTrue(RenderPresentation.neutral().smoothBanding());
    }
}
