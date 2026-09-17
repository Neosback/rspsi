package com.rspsi.editor.render;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LightingProfileTest {
    @Test
    void defaultProfileMatchesTheOsrsDirectionalBaseline() {
        LightingProfile profile = LightingProfile.osrs();

        assertEquals(-50, profile.lightX());
        assertEquals(-10, profile.lightY());
        assertEquals(-50, profile.lightZ());
        assertEquals(96, profile.ambient());
        assertEquals(768, profile.intensityFactor());
        assertEquals(65536, profile.heightScale());
    }

    @Test
    void profileChangesRemainInNeutralLightingAndExposureDoesNot() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(
                0, 0, 256, 0, 0, 0, 0, 0, 0, java.util.List.of()));

        var defaultLight = TerrainLighting.build(document, LightingProfile.osrs());
        var dimmer = TerrainLighting.build(document, new LightingProfile(
                -50, -10, -50, 80, 768, 65536, 71, true, true, 0.8));

        assertNotEquals(defaultLight, dimmer);
        assertEquals(1.0, LightingExposure.neutral().value());
    }
}
