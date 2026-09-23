package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedSceneZoneLayoutTest {
    @Test
    void matchesRuneLiteGpuZoneDimensionsAndFiveZoneOffset() {
        ExtendedSceneZoneLayout layout = ExtendedSceneZoneLayout.runeLite();

        assertEquals(8, ExtendedSceneZoneLayout.ZONE_SIZE);
        assertEquals(13, layout.sceneZoneCount());
        assertEquals(23, layout.extendedZoneCount());
        assertEquals(5, layout.sceneZoneOffset());
        assertEquals(5, layout.normalMinZone());
        assertEquals(18, layout.normalMaxZoneExclusive());

        assertEquals(new ExtendedSceneZoneLayout.Point(5, 5),
                layout.toExtendedZone(0, 0));
        assertEquals(new ExtendedSceneZoneLayout.Point(17, 17),
                layout.toExtendedZone(12, 12));
        assertEquals(new ExtendedSceneZoneLayout.Point(0, 0),
                layout.toSceneZone(5, 5).orElseThrow());
        assertEquals(new ExtendedSceneZoneLayout.Point(12, 12),
                layout.toSceneZone(17, 17).orElseThrow());

        assertTrue(layout.isBorderZone(0, 0));
        assertTrue(layout.isBorderZone(4, 5));
        assertTrue(layout.isBorderZone(18, 17));
        assertTrue(layout.isBorderZone(22, 22));
        assertFalse(layout.isBorderZone(5, 5));
        assertFalse(layout.isBorderZone(17, 17));
    }

    @Test
    void reproducesRuneLiteTopLevelZoneTranslation() {
        ExtendedSceneZoneLayout layout = ExtendedSceneZoneLayout.runeLite();

        assertEquals(new ExtendedSceneZoneLayout.Point(-5, -5),
                layout.renderZone(0, 0, true));
        assertEquals(new ExtendedSceneZoneLayout.Point(0, 0),
                layout.renderZone(5, 5, true));
        assertEquals(new ExtendedSceneZoneLayout.Point(12, 12),
                layout.renderZone(17, 17, true));
        assertEquals(new ExtendedSceneZoneLayout.Point(17, 17),
                layout.renderZone(22, 22, true));

        // RuneLite sub-worldviews do not subtract the top-level scene offset.
        assertEquals(new ExtendedSceneZoneLayout.Point(5, 5),
                layout.renderZone(5, 5, false));
    }

    @Test
    void roundTripsExtendedZonesAgainstAbsoluteWorldResidency() {
        SceneWindow window = new SceneWindow(
                new WorldRegionWindow(50, 50, 1, 1, Map.of()),
                3200, 3200, 4, 0, 0, -1,
                Set.of(), List.of());
        ExtendedSceneZoneLayout layout = window.extendedSceneZoneLayout();

        WorldZoneCoordinate southWestBorder = layout.worldZone(window, 2, 0, 0);
        WorldZoneCoordinate normalOrigin = layout.worldZone(window, 2, 5, 5);
        WorldZoneCoordinate normalFarCorner = layout.worldZone(window, 2, 17, 17);
        WorldZoneCoordinate northEastBorder = layout.worldZone(window, 2, 22, 22);

        assertEquals(new WorldZoneCoordinate(2, 395, 395), southWestBorder);
        assertEquals(new WorldZoneCoordinate(2, 400, 400), normalOrigin);
        assertEquals(new WorldZoneCoordinate(2, 412, 412), normalFarCorner);
        assertEquals(new WorldZoneCoordinate(2, 417, 417), northEastBorder);

        assertEquals(new ExtendedSceneZoneLayout.Point(0, 0),
                layout.extendedZone(window, southWestBorder).orElseThrow());
        assertEquals(new ExtendedSceneZoneLayout.Point(5, 5),
                layout.extendedZone(window, normalOrigin).orElseThrow());
        assertEquals(new ExtendedSceneZoneLayout.Point(17, 17),
                layout.extendedZone(window, normalFarCorner).orElseThrow());
        assertEquals(new ExtendedSceneZoneLayout.Point(22, 22),
                layout.extendedZone(window, northEastBorder).orElseThrow());

        assertTrue(layout.extendedZone(window,
                new WorldZoneCoordinate(2, 394, 400)).isEmpty());
        assertTrue(layout.extendedZone(window,
                new WorldZoneCoordinate(2, 418, 400)).isEmpty());
    }
}
