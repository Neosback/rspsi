package com.rspsi.osrs.rules.terrain;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.OsrsTerrainColorMath;

import java.util.Objects;

/**
 * Formal OSRS underlay floor blending rules.
 *
 * <p>In the OSRS client, underlay color is blended once per tile (class470 var36)
 * across a 5-tile radius window using weighted hue, chroma, luminance, and saturation.</p>
 */
public final class FloorBlendRules {
    public static final int UNDERLAY_BLEND_RADIUS = 5;

    private FloorBlendRules() {}

    /**
     * Blends underlay HSL for a tile at (plane, x, y) in the given document.
     *
     * @return packed HSL color, or -1 if no underlay is present
     */
    public static int blendUnderlay(WorldDocument document, DefinitionProvider definitions,
                                   int plane, int x, int y) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");

        int localX = Math.max(0, Math.min(document.width() - 1, x));
        int localY = Math.max(0, Math.min(document.length() - 1, y));
        if (document.tile(plane, localX, localY).snapshot().underlayId() <= 0) {
            return -1;
        }

        long saturation = 0;
        long luminance = 0;
        long weightedHue = 0;
        long chroma = 0;
        int count = 0;

        for (int sampleX = Math.max(0, x - UNDERLAY_BLEND_RADIUS + 1);
             sampleX <= Math.min(document.width() - 1, x + UNDERLAY_BLEND_RADIUS); sampleX++) {
            for (int sampleY = Math.max(0, y - UNDERLAY_BLEND_RADIUS + 1);
                 sampleY <= Math.min(document.length() - 1, y + UNDERLAY_BLEND_RADIUS); sampleY++) {
                if (sampleX < 0 || sampleX >= document.width()
                        || sampleY < 0 || sampleY >= document.length()) continue;
                int id = document.tile(plane, sampleX, sampleY).snapshot().underlayId();
                if (id <= 0) continue;
                FloorDefinitionView floor = definitions.underlay(id - 1).orElse(null);
                if (floor == null || floor.chroma() <= 0) continue;
                saturation += floor.saturation();
                luminance += floor.luminance();
                weightedHue += floor.weightedHue();
                chroma += floor.chroma();
                count++;
            }
        }

        if (chroma == 0) return -1;
        int averageHue = (int) (weightedHue * 256 / chroma);
        int averageSaturation = (int) (saturation / Math.max(1, count));
        int averageLuminance = (int) (luminance / Math.max(1, count));
        return OsrsTerrainColorMath.packHsl(averageHue, averageSaturation, averageLuminance);
    }
}
