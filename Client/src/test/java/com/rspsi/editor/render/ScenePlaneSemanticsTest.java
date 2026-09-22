package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenePlaneSemanticsTest {
    @Test
    void normalTileKeepsAuthoredSceneRenderAndCullPlanesAligned() {
        ScenePlaneSemantics planes = ScenePlaneSemantics.resolve(2, 0, false);

        assertEquals(2, planes.authoredPlane());
        assertEquals(2, planes.scenePlane());
        assertEquals(2, planes.renderLevel());
        assertEquals(2, planes.planeCullLevel());
    }

    @Test
    void bridgeLinkShiftsCurrentScenePlaneButKeepsOriginalRenderLevel() {
        ScenePlaneSemantics planes = ScenePlaneSemantics.resolve(
                2, OsrsTileFlags.BRIDGE, true);

        assertEquals(2, planes.authoredPlane());
        assertEquals(1, planes.scenePlane());
        assertEquals(2, planes.renderLevel());
        assertEquals(1, planes.planeCullLevel());
    }

    @Test
    void forcePlaneZeroFlagOverridesTheMinimumCullLevel() {
        ScenePlaneSemantics planes = ScenePlaneSemantics.resolve(
                3, OsrsTileFlags.MINIMAP_BRIDGE, false);

        assertEquals(3, planes.authoredPlane());
        assertEquals(3, planes.scenePlane());
        assertEquals(3, planes.renderLevel());
        assertEquals(0, planes.planeCullLevel());
    }
}
