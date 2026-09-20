package com.rspsi.editor.brush;

import com.rspsi.editor.brush.builtin.CircleBrush;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushEngineTest {

    @Test
    void sampleProducesOneCanonicalMaskForPreviewAndEditing() {
        BrushEngine engine = new BrushEngine();
        WorldDocument world = new WorldDocument(8, 8, 1);

        BrushMask mask = engine.sample(
                new CircleBrush(), 2, new WorldTile(0, 4, 4), world, new WorldWindow(0, 0, 8, 8));

        assertFalse(mask.isEmpty());
        assertEquals(new WorldTile(0, 4, 4), mask.center());
        assertTrue(mask.samples().stream()
                .anyMatch(sample -> sample.local().equals(new LocalTile(0, 4, 4))));
        assertEquals(2, mask.bounds().minX());
        assertEquals(6, mask.bounds().maxX());
    }

    @Test
    void samplingAtDocumentEdgeNeverWrapsToOppositeEdge() {
        BrushEngine engine = new BrushEngine();
        WorldDocument world = new WorldDocument(4, 4, 1);
        BrushMask mask = engine.sample(
                engine.brush("square"), 1, new WorldTile(0, 0, 0), world, new WorldWindow(0, 0, 4, 4));

        assertTrue(mask.samples().stream().allMatch(sample ->
                sample.local().x() >= 0 && sample.local().x() <= 1
                        && sample.local().y() >= 0 && sample.local().y() <= 1));
        assertFalse(mask.samples().stream().anyMatch(sample ->
                sample.local().x() == 3 || sample.local().y() == 3));
    }

    @Test
    void falloffFunctionsAreDeterministic() {
        assertEquals(1.0, BrushEngine.applyFalloff(BrushEngine.Falloff.LINEAR, 0.0), 1e-9);
        assertEquals(0.0, BrushEngine.applyFalloff(BrushEngine.Falloff.LINEAR, 1.0), 1e-9);
        assertTrue(BrushEngine.applyFalloff(BrushEngine.Falloff.GAUSSIAN, 0.5) > 0.0);
    }
}
