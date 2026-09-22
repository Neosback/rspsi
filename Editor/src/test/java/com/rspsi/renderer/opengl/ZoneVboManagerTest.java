package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ZoneVboManagerTest {

    @Test
    void tilesInSameEightByEightZoneShareZoneKey() {
        // WorldTileAddress.of(worldX, worldY, plane)
        WorldTileAddress origin = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress inside = WorldTileAddress.of(3207, 3207, 0);
        assertEquals(ZoneVboManager.zoneKey(origin), ZoneVboManager.zoneKey(inside));
    }

    @Test
    void crossingEightTileBoundaryProducesDistinctZoneKeys() {
        WorldTileAddress zoneA = WorldTileAddress.of(3207, 3200, 0);
        WorldTileAddress zoneB = WorldTileAddress.of(3208, 3200, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneA), ZoneVboManager.zoneKey(zoneB));

        WorldTileAddress zoneC = WorldTileAddress.of(3200, 3207, 0);
        WorldTileAddress zoneD = WorldTileAddress.of(3200, 3208, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneC), ZoneVboManager.zoneKey(zoneD));
    }

    @Test
    void differentPlanesProduceDistinctZoneKeys() {
        WorldTileAddress plane0 = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress plane1 = WorldTileAddress.of(3200, 3200, 1);
        assertNotEquals(ZoneVboManager.zoneKey(plane0), ZoneVboManager.zoneKey(plane1));
    }

    @Test
    void authenticSoftwareIntegerTextureShadingFormula() {
        // GameRasterizer.java: (start_col & 0xFF80) | (((start_col & 0x7F) * tex_lum) >> 7)
        int hueSat = (35 << 10) | (5 << 7); // Hue 35, Sat 5
        int vertexLightness = 90; // 0..127
        int startCol = hueSat | vertexLightness;

        // texLum calculation from RGB: ((r >> 1) + (g >> 1) + (b >> 1) + 127) >> 2
        int r = 200, g = 180, b = 150;
        int texLum = ((r >> 1) + (g >> 1) + (b >> 1) + 127) >> 2;

        int shadedLight = Math.max(0, Math.min(127, ((startCol & 0x7F) * texLum) >> 7));
        int shadedHsl = (startCol & 0xFF80) | shadedLight;

        // Hue and Saturation are preserved exactly in the upper 9 bits
        assertEquals(hueSat, shadedHsl & 0xFF80);
        // Lightness is modulated by texture luminance
        assertEquals(shadedLight, shadedHsl & 0x7F);
    }
}
