package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds OSRS HSL/material inputs without exposing cache-library types. */
public final class TerrainAppearanceBuilder {
    private static final int UNDERLAY_BLEND_RADIUS = 5;

    public Map<TileCoordinate, TerrainAppearance> build(WorldDocument document,
                                                         DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Map<TileCoordinate, TerrainAppearance> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    result.put(new TileCoordinate(plane, x, y), appearance(document, definitions,
                            plane, x, y, tile));
                }
            }
        }
        return result;
    }

    private TerrainAppearance appearance(WorldDocument document, DefinitionProvider definitions,
                                         int plane, int x, int y, TileSnapshot tile) {
        // The client blends underlay color once per TILE (class470's var36),
        // not once per corner. All four corners of a tile share that single
        // hue/saturation; only lightness varies per corner, via the separate
        // slope/AO shading term (TerrainLighting -> adjustPackedHslLight).
        // Blending independently per corner (as this used to do, sampling a
        // shifted window for each of the four corners) re-derives hue and
        // saturation per corner instead of reusing one tile-wide color,
        // producing a softer/off-color blend at underlay-type boundaries
        // that the real client never produces.
        int underlayHsl = blendedUnderlay(document, definitions, plane, x, y);
        FloorDefinitionView overlay = tile.overlayId() <= 0
                ? null : definitions.overlay(tile.overlayId() - 1).orElse(null);
        int overlayHsl = overlay == null ? -1
                : OsrsTerrainColorMath.packHsl(overlay.hue(), overlay.saturation(), overlay.luminance());
        int secondaryHsl = overlay == null || overlay.secondaryRgb() < 0
                ? -1 : OsrsTerrainColorMath.packHsl(overlay.secondaryHue(),
                overlay.secondarySaturation(), overlay.secondaryLuminance());
        int textureId = overlay == null ? -1 : overlay.texture();
        int textureHsl = -1;
        if (textureId >= 0) {
            TextureDefinitionView texture = definitions.texture(textureId).orElse(null);
            textureHsl = texture == null ? -1 : texture.averageHsl();
        }
        // The client keeps two overlay colors: the render HSL and the
        // minimap/fallback HSL. Textured overlays deliberately use -1 for
        // render HSL (the renderer supplies light-only vertex values), while
        // the texture average is retained separately for minimap use. The
        // magenta primary-color sentinel is -2 in the client scene model.
        int overlayMinimapHsl = overlayHsl;
        if (overlay == null) {
            overlayHsl = -1;
        } else if (textureId >= 0) {
            overlayHsl = -1;
            overlayMinimapHsl = textureHsl;
        } else if ((overlay.rgb() & 0xFFFFFF) == 0xFF00FF) {
            overlayHsl = -2;
            overlayMinimapHsl = -2;
        }
        if (overlay != null && secondaryHsl >= 0) {
            overlayMinimapHsl = secondaryHsl;
        }
        boolean hidden = overlayHsl == -2;
        // All four corners share this tile's single blended hue/saturation;
        // TerrainPacketBuilder varies only lightness per corner.
        return new TerrainAppearance(underlayHsl, underlayHsl, underlayHsl, underlayHsl, underlayHsl,
                overlayHsl, secondaryHsl, textureId, textureHsl,
                tile.overlayShape(), tile.overlayRotation() & 3, hidden, overlayMinimapHsl);
    }

    private int blendedUnderlay(WorldDocument document, DefinitionProvider definitions,
                                int plane, int x, int y) {
        return com.rspsi.osrs.rules.terrain.FloorBlendRules.blendUnderlay(document, definitions, plane, x, y);
    }
}
