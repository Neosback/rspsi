package com.rspsi.editor.render;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSceneParityTest {
    @Test
    void identicalNeutralScenesMatch() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        RenderScene first = new RenderSceneBuilder().build(document);
        RenderScene second = new RenderSceneBuilder().build(document);

        RenderSceneParity.Report report = RenderSceneParity.compare(first, second);

        assertTrue(report.matches());
        assertEquals(4, report.comparedTiles());
        assertEquals(0, report.differenceCount());
    }

    @Test
    void reportsAChangedTileAndKeepsTheReportBounded() {
        WorldDocument expectedDocument = new WorldDocument(2, 2, 1);
        WorldDocument actualDocument = new WorldDocument(2, 2, 1);
        actualDocument.tile(0, 1, 1).restore(new TileSnapshot(
                16, 16, 16, 16, 0, 0, 0, 0, 0, java.util.List.of()));

        RenderSceneParity.Report report = RenderSceneParity.compare(
                new RenderSceneBuilder().build(expectedDocument),
                new RenderSceneBuilder().build(actualDocument));

        assertFalse(report.matches());
        assertTrue(report.differenceCount() >= 1);
        assertTrue(report.differences().stream().anyMatch(value ->
                value.scope().equals("terrain")
                        && value.location().contains("plane=0")
                        && value.location().contains("x=1")
                        && value.location().contains("y=1")));
    }
}
