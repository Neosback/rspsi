package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainSceneCompiler;
import com.rspsi.editor.model.TileCoordinate;

import java.util.Arrays;
import java.util.Objects;

/**
 * Builds a deterministic, cache-neutral terrain minimap raster.
 *
 * <p>This is the first semantic baseline for minimap fixtures. It selects an
 * overlay when present, otherwise averages the current underlay with its
 * cardinal neighbors. Missing definitions use a visible deterministic
 * fallback so cache problems do not silently become transparent pixels.</p>
 */
public final class MinimapBuilder {
    private static final int MISSING_COLOR = 0xFF9CA3AF;
    private static final int BLOCKED_COLOR = 0xFF1F2937;
    /** TSPS/RuneScape's initial pixel value for an empty scene tile. */
    private static final int EMPTY_SCENE_PIXEL = 0xFF000001;
    /**
     * The client palette used by TSPS for scene/minimap HSL values. Keeping
     * the palette locally makes the raster deterministic without coupling the
     * editor to a client renderer implementation.
     */
    private static final int[] OSRS_HSL_RGB_MAP = buildOsrsPalette();

    // The 4x4 masks and rotation permutations are the client-side shaped-tile
    // raster contract used by TSPS. They are kept here as data, not coupled to
    // either the legacy renderer or a frontend image API.
    private static final int[][] TILE_SHAPE = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1},
            {0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0},
            {1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1},
            {1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1}
    };

    private static final int[][] TILE_ROTATION = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {12, 8, 4, 0, 13, 9, 5, 1, 14, 10, 6, 2, 15, 11, 7, 3},
            {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0},
            {3, 7, 11, 15, 2, 6, 10, 14, 1, 5, 9, 13, 0, 4, 8, 12}
    };

    public MinimapImage build(WorldDocument document, int plane, DefinitionProvider definitions) {
        return build(document, plane, definitions, true);
    }

    public MinimapImage build(WorldDocument document, int plane,
                              DefinitionProvider definitions, boolean blendUnderlays) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        if (plane < 0 || plane >= document.planes()) {
            throw new IllegalArgumentException("Plane outside document: " + plane);
        }
        int[] pixels = new int[document.width() * document.length()];
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                TileSnapshot tile = document.tile(plane, x, y).snapshot();
                int index = y * document.width() + x;
                if ((tile.flags() & OsrsTileFlags.BLOCK_MAP_SQUARE) != 0) {
                    pixels[index] = BLOCKED_COLOR;
                } else if (tile.overlayId() != 0) {
                    pixels[index] = color(overlayDefinition(definitions, tile.overlayId()),
                            tile.overlayId(), false);
                } else if (blendUnderlays) {
                    pixels[index] = blendedUnderlay(document, plane, x, y, definitions);
                } else {
                    pixels[index] = color(underlayDefinition(definitions, tile.underlayId()),
                            tile.underlayId(), true);
                }
            }
        }
        return new MinimapImage(plane, document.width(), document.length(), pixels);
    }

    /**
     * Builds a TSPS-compatible shaped-tile raster at four pixels per tile.
     * This is intentionally additive: {@link #build(WorldDocument, int,
     * DefinitionProvider)} remains the stable one-pixel semantic baseline.
     * Colors use the OSRS HSL palette and radius-5 underlay blend when the
     * neutral definitions provide the required metadata, with deterministic
     * RGB fallback for incomplete definitions. Optional neutral map-scene
     * sprites are composed after terrain and before wall markers.
     */
    public MinimapImage buildShaped(WorldDocument document, int plane,
                                    DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        if (plane < 0 || plane >= document.planes()) {
            throw new IllegalArgumentException("Plane outside document: " + plane);
        }

        int width = document.width() * 4;
        int height = document.length() * 4;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, EMPTY_SCENE_PIXEL);
        java.util.Map<TileCoordinate, CompiledTerrainTile> compiledTerrain =
                new TerrainSceneCompiler().compile(document, definitions);
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                // SceneBuilder reserves the outer tile ring for neighbour
                // height/blend context and does not create tile models there.
                if (x == 0 || y == 0 || x == document.width() - 1
                        || y == document.length() - 1) continue;
                TileSnapshot tile = document.tile(plane, x, y).snapshot();
                boolean bridgeLinked = plane == 0
                        && plane + 1 < document.planes()
                        && (document.tile(plane + 1, x, y).snapshot().flags()
                        & OsrsTileFlags.BRIDGE) != 0;
                if (!bridgeLinked
                        && (tile.flags() & OsrsTileFlags.MINIMAP_HIDDEN) == 0
                        && !(plane > 0 && (tile.flags() & OsrsTileFlags.BRIDGE) != 0)) {
                    drawShapedTile(document, plane, x, y, definitions, compiledTerrain, pixels, width);
                }
                // TSPS/OSRS relinks a bridge column before minimap rendering:
                // plane 0 contains the authored plane-1 tile, and the base
                // tile is retained only as linked-below metadata. Render the
                // bridge source alone so non-overlay pixels remain the
                // minimap sentinel instead of leaking the base tile color.
                if (bridgeLinked) {
                    drawShapedTile(document, plane + 1, x, y, definitions, compiledTerrain, pixels, width);
                } else if (plane < document.planes() - 1
                        && (document.tile(plane + 1, x, y).snapshot().flags()
                        & OsrsTileFlags.MINIMAP_BRIDGE) != 0) {
                    drawShapedTile(document, plane + 1, x, y, definitions, pixels, width);
                }
            }
        }
        drawWallMarkers(document, plane, definitions, pixels, width);
        return new MinimapImage(plane, width, height, pixels);
    }

    private static void drawShapedTile(WorldDocument document, int sourcePlane, int x, int y,
                                       DefinitionProvider definitions,
                                       java.util.Map<TileCoordinate, CompiledTerrainTile> compiledTerrain,
                                       int[] pixels, int width) {
        TileSnapshot tile = document.tile(sourcePlane, x, y).snapshot();
        boolean hasUnderlay = tile.underlayId() > 0;
        boolean hasOverlay = tile.overlayId() > 0;
        if (!hasUnderlay && !hasOverlay) return;

        CompiledTerrainTile compiled = compiledTerrain.get(
                new TileCoordinate(sourcePlane, x, y));
        int underlay = 0;
        int overlay = 0xFF000000;
        if (compiled != null) {
            int underlayHsl = compiled.appearance().underlayHsl();
            if (hasUnderlay && underlayHsl >= 0) {
                underlay = 0xFF000000
                        | osrsPaletteColor(adjustUnderlayLight(underlayHsl, 96));
            }
            int overlayHsl = compiled.appearance().overlayMinimapHsl();
            if (overlayHsl >= 0) {
                overlay = 0xFF000000
                        | osrsPaletteColor(adjustOverlayLight(overlayHsl, 96));
            } else if (overlayHsl == -2) {
                overlay = 0xFF000000;
            } else {
                overlay = osrsColor(overlayDefinition(definitions, tile.overlayId()),
                        tile.overlayId(), false, definitions);
            }
        } else {
            underlay = hasUnderlay
                    ? blendedOsrsUnderlay(document, sourcePlane, x, y, definitions)
                    : 0;
            overlay = osrsColor(overlayDefinition(definitions, tile.overlayId()),
                    tile.overlayId(), false, definitions);
        }
        int shape = hasOverlay ? tile.overlayShape() + 1 : 0;
        if (shape < 0 || shape >= TILE_SHAPE.length) {
            throw new IllegalArgumentException("Encoded overlay shape must be between 0 and 11");
        }
        int rotation = tile.overlayRotation();
        if (rotation < 0 || rotation >= TILE_ROTATION.length) {
            throw new IllegalArgumentException("Tile rotation must be between 0 and 3");
        }
        int[] mask = TILE_SHAPE[shape];
        int[] permutation = TILE_ROTATION[rotation];
        int outputY = document.length() - 1 - y;
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int maskIndex = row * 4 + column;
                boolean overlayPixel = mask[permutation[maskIndex]] != 0;
                // With no underlay TSPS leaves the non-overlay part at the
                // scene's initial sentinel value.
                if (!overlayPixel && !hasUnderlay) continue;
                int rgb = overlayPixel ? overlay : underlay;
                pixels[(outputY * 4 + row) * width + x * 4 + column] = rgb;
            }
        }
    }

    /**
     * Draws the small, cache-independent wall marks used by the OSRS minimap.
     * Map-scene sprites are deliberately deferred until the neutral asset
     * provider exposes sprite pixels; walls still provide useful parity and
     * editor diagnostics without coupling this raster to a cache library.
     */
    private static void drawWallMarkers(WorldDocument document, int plane,
                                        DefinitionProvider definitions, int[] pixels, int width) {
        final int wallColor = 0xFFEEEEEE;
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                if (x == 0 || y == 0 || x == document.width() - 1
                        || y == document.length() - 1) continue;
                int outputY = document.length() - 1 - y;
                java.util.List<com.rspsi.editor.model.WorldObject> objects =
                        new java.util.ArrayList<>();
                TileSnapshot current = document.tile(plane, x, y).snapshot();
                if ((current.flags() & OsrsTileFlags.MINIMAP_HIDDEN) == 0
                        && !(plane > 0 && (current.flags() & OsrsTileFlags.BRIDGE) != 0)) {
                    objects.addAll(current.objects());
                }
                if (plane < document.planes() - 1
                        && (document.tile(plane + 1, x, y).snapshot().flags()
                        & (OsrsTileFlags.MINIMAP_BRIDGE | OsrsTileFlags.BRIDGE)) != 0) {
                    objects.addAll(document.tile(plane + 1, x, y).snapshot().objects());
                }
                // TSPS/OSRS renders one scene layer at a time: walls first,
                // then game objects, then ground decorations. WorldDocument
                // intentionally preserves cache placement order, which is
                // not the same thing as renderer layer order when locations
                // overlap. Keep the renderer contract explicit here.
                objects.sort(java.util.Comparator.comparingInt(MinimapBuilder::minimapLayer));
                for (var object : objects) {
                    var objectDefinition = definitions.object(object.id());
                    if (objectDefinition.isPresent() && objectDefinition.get().mapSceneId() >= 0) {
                        var sprite = definitions.mapScene(objectDefinition.get().mapSceneId());
                        if (sprite.isPresent()) {
                            drawMapScene(document, x, y, objectDefinition.get(), sprite.get(), pixels, width);
                        }
                        // The client suppresses the fallback wall mark for a
                        // loc that declares a map-scene sprite, even when the
                        // optional sprite group is unavailable.
                        continue;
                    }
                    int markerColor = objectDefinition
                            .filter(com.rspsi.cache.definition.ObjectDefinitionView::interactive)
                            .map(objectDefinitionValue -> 0xFFEE0000)
                            .orElse(wallColor);
                    int offset = x * 4 + outputY * width * 4;
                    int type = object.type();
                    int rotation = object.rotation();
                    if (type == 0 || type == 2) {
                        if (rotation == 0) {
                            for (int row = 0; row < 4; row++) pixels[offset + row * width] = markerColor;
                        } else if (rotation == 1) {
                            for (int column = 0; column < 4; column++) pixels[offset + column] = markerColor;
                        } else if (rotation == 2) {
                            for (int row = 0; row < 4; row++) pixels[offset + row * width + 3] = markerColor;
                        } else {
                            for (int column = 0; column < 4; column++) pixels[offset + width * 3 + column] = markerColor;
                        }
                        if (type == 2) {
                            if (rotation == 3) {
                                for (int row = 0; row < 4; row++) pixels[offset + row * width] = markerColor;
                            } else if (rotation == 0) {
                                for (int column = 0; column < 4; column++) pixels[offset + column] = markerColor;
                            } else if (rotation == 1) {
                                for (int row = 0; row < 4; row++) pixels[offset + row * width + 3] = markerColor;
                            } else {
                                for (int column = 0; column < 4; column++) pixels[offset + width * 3 + column] = markerColor;
                            }
                        }
                    } else if (type == 3) {
                        int pixelX = rotation == 0 || rotation == 3 ? 0 : 3;
                        int pixelY = rotation == 0 || rotation == 1 ? 0 : 3;
                        pixels[offset + pixelY * width + pixelX] = markerColor;
                    } else if (type == 9) {
                        if (rotation == 0 || rotation == 2) {
                            pixels[offset + width * 3] = markerColor;
                            pixels[offset + width * 2 + 1] = markerColor;
                            pixels[offset + width + 2] = markerColor;
                            pixels[offset + 3] = markerColor;
                        } else {
                            pixels[offset] = markerColor;
                            pixels[offset + width + 1] = markerColor;
                            pixels[offset + width * 2 + 2] = markerColor;
                            pixels[offset + width * 3 + 3] = markerColor;
                        }
                    }
                }
            }
        }
    }

    private static int minimapLayer(com.rspsi.editor.model.WorldObject object) {
        return object.category().isKnown() ? object.category().layerId() : Integer.MAX_VALUE;
    }

    private static void drawMapScene(WorldDocument document, int tileX, int tileY,
                                     com.rspsi.cache.definition.ObjectDefinitionView definition,
                                     MapSceneSpriteView sprite, int[] pixels, int width) {
        int originX = tileX * 4
                + (definition.width() * 4 - sprite.width()) / 2 + sprite.offsetX();
        int originY = (document.length() - tileY - definition.length()) * 4
                + (definition.length() * 4 - sprite.height()) / 2
                + sprite.offsetY();
        int[] spritePixels = sprite.argb();
        for (int y = 0; y < sprite.height(); y++) {
            int outputY = originY + y;
            if (outputY < 0 || outputY >= document.length() * 4) continue;
            for (int x = 0; x < sprite.width(); x++) {
                int outputX = originX + x;
                if (outputX < 0 || outputX >= document.width() * 4) continue;
                int argb = spritePixels[y * sprite.width() + x];
                if (argb != 0) pixels[outputY * width + outputX] = argb;
            }
        }
    }

    private static int blendedUnderlay(WorldDocument document, int plane, int x, int y,
                                       DefinitionProvider definitions) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;
        for (int offset = 0; offset < 5; offset++) {
            int sampleX = x;
            int sampleY = y;
            if (offset == 1) sampleX--;
            if (offset == 2) sampleX++;
            if (offset == 3) sampleY--;
            if (offset == 4) sampleY++;
            if (sampleX < 0 || sampleX >= document.width()
                    || sampleY < 0 || sampleY >= document.length()) continue;
            TileSnapshot sample = document.tile(plane, sampleX, sampleY).snapshot();
            if (sample.underlayId() <= 0) continue;
            int rgb = color(underlayDefinition(definitions, sample.underlayId()),
                    sample.underlayId(), true);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            samples++;
        }
        if (samples == 0) return MISSING_COLOR;
        return 0xFF000000 | ((red / samples) << 16) | ((green / samples) << 8) | (blue / samples);
    }

    /** Uses the OSRS radius-5 HSL blend when neutral definitions provide it. */
    private static int blendedOsrsUnderlay(WorldDocument document, int plane, int x, int y,
                                           DefinitionProvider definitions) {
        int weightedHue = 0;
        int hueMultiplier = 0;
        int saturation = 0;
        int luminance = 0;
        int samples = 0;
        // This is the effective window of TSPS's sliding blend accumulator:
        // radius five contributes the current coordinate through +5, while
        // the removal step has already dropped coordinate -5.
        for (int sampleX = Math.max(0, x - 4); sampleX <= Math.min(document.width() - 1, x + 5); sampleX++) {
            for (int sampleY = Math.max(0, y - 4); sampleY <= Math.min(document.length() - 1, y + 5); sampleY++) {
                TileSnapshot sample = document.tile(plane, sampleX, sampleY).snapshot();
                if (sample.underlayId() <= 0) continue;
                java.util.Optional<FloorDefinitionView> definition = underlayDefinition(
                        definitions, sample.underlayId());
                if (definition.isEmpty() || definition.get().chroma() <= 0) continue;
                FloorDefinitionView floor = definition.get();
                weightedHue += floor.weightedHue();
                hueMultiplier += floor.chroma();
                saturation += floor.saturation();
                luminance += floor.luminance();
                samples++;
            }
        }
        if (samples > 0 && hueMultiplier > 0) {
            int hue = weightedHue * 256 / hueMultiplier;
            int hsl = packHsl(hue, saturation / samples, luminance / samples);
            return 0xFF000000 | osrsPaletteColor(adjustUnderlayLight(hsl, 96));
        }
        return blendedUnderlay(document, plane, x, y, definitions);
    }

    private static int osrsColor(java.util.Optional<FloorDefinitionView> definition,
                                 int id, boolean underlay, DefinitionProvider definitions) {
        if (definition.isPresent()) {
            FloorDefinitionView floor = definition.get();
            int minimapHsl = -1;
            if (!underlay && floor.texture() >= 0) {
                java.util.Optional<com.rspsi.cache.definition.TextureDefinitionView> texture =
                        definitions.texture(floor.texture());
                if (texture.isPresent() && texture.get().averageHsl() >= 0) {
                    minimapHsl = texture.get().averageHsl();
                }
            }
            int hue = floor.hue();
            int saturation = floor.saturation();
            int luminance = floor.luminance();
            if (!underlay && floor.secondaryRgb() != -1) {
                hue = floor.secondaryHue();
                saturation = floor.secondarySaturation();
                luminance = floor.secondaryLuminance();
            }
            if (!underlay && floor.rgb() == 0xFF00FF && minimapHsl < 0) {
                minimapHsl = -2;
            } else if (minimapHsl < 0
                    && (hue != 0 || saturation != 0 || luminance != 0 || floor.chroma() > 0)) {
                minimapHsl = packHsl(hue, saturation, luminance);
            }
            if (!underlay && floor.secondaryRgb() != -1) {
                boolean hasSecondaryHsl = hue != 0 || saturation != 0 || luminance != 0;
                if (hasSecondaryHsl) {
                    minimapHsl = packHsl(hue, saturation, luminance);
                }
            }
            if (minimapHsl == -2) {
                return 0xFF000000;
            }
            if (minimapHsl >= 0) {
                return 0xFF000000 | osrsPaletteColor(adjustOverlayLight(minimapHsl, 96));
            }
            if (!underlay && floor.secondaryRgb() != -1) {
                return 0xFF000000 | (floor.secondaryRgb() & 0xFFFFFF);
            }
            return 0xFF000000 | (floor.rgb() & 0xFFFFFF);
        }
        return color(definition, id, underlay);
    }

    private static int packHsl(int hue, int saturation, int luminance) {
        return OsrsTerrainColorMath.packHsl(hue, saturation, luminance);
    }

    private static int adjustUnderlayLight(int hsl, int light) {
        return OsrsTerrainColorMath.adjustPackedHslLight(hsl, light);
    }

    private static int adjustOverlayLight(int hsl, int light) {
        return OsrsTerrainColorMath.adjustPackedHslLight(hsl, light);
    }

    private static int osrsPaletteColor(int hsl) {
        return hsl >= 0 && hsl < OSRS_HSL_RGB_MAP.length ? OSRS_HSL_RGB_MAP[hsl] : 1;
    }

    /** Exact port of TSPS ColorUtil.buildPalette(0.8, 0, 512). */
    private static int[] buildOsrsPalette() {
        int[] palette = new int[1 << 16];
        int paletteIndex = 0;
        for (int hueBand = 0; hueBand < 512; hueBand++) {
            double hue = (hueBand >> 3) / 64.0 + 0.0078125;
            double saturation = (hueBand & 7) / 8.0 + 0.0625;
            for (int lightness = 0; lightness < 128; lightness++) {
                double luminance = lightness / 128.0;
                double red = luminance;
                double green = luminance;
                double blue = luminance;
                if (saturation != 0.0) {
                    double max = luminance < 0.5
                            ? luminance * (1.0 + saturation)
                            : luminance + saturation - luminance * saturation;
                    double min = 2.0 * luminance - max;
                    red = paletteChannel(min, max, hue + 1.0 / 3.0);
                    green = paletteChannel(min, max, hue);
                    blue = paletteChannel(min, max, hue - 1.0 / 3.0);
                }
                int rawRed = (int) (red * 256.0);
                int rawGreen = (int) (green * 256.0);
                int rawBlue = (int) (blue * 256.0);
                int rgb = brightenRgb((rawRed << 16) | (rawGreen << 8) | rawBlue, 0.8);
                palette[paletteIndex++] = rgb == 0 ? 1 : rgb;
            }
        }
        return palette;
    }

    private static double paletteChannel(double min, double max, double value) {
        if (value > 1.0) value--;
        if (value < 0.0) value++;
        if (6.0 * value < 1.0) return min + (max - min) * 6.0 * value;
        if (2.0 * value < 1.0) return max;
        if (3.0 * value < 2.0) return min + (max - min) * (2.0 / 3.0 - value) * 6.0;
        return min;
    }

    private static int brightenRgb(int rgb, double brightness) {
        int red = (int) (Math.pow(((rgb >> 16) & 0xFF) / 256.0, brightness) * 256.0);
        int green = (int) (Math.pow(((rgb >> 8) & 0xFF) / 256.0, brightness) * 256.0);
        int blue = (int) (Math.pow((rgb & 0xFF) / 256.0, brightness) * 256.0);
        return (red << 16) | (green << 8) | blue;
    }

    private static int color(java.util.Optional<FloorDefinitionView> definition,
                             int id, boolean underlay) {
        if (definition.isPresent()) return 0xFF000000 | (definition.get().rgb() & 0xFFFFFF);
        if (id == 0) return 0xFF000000;
        int seed = id * (underlay ? 73 : 97);
        int red = 64 + Math.floorMod(seed, 128);
        int green = 64 + Math.floorMod(seed * 3, 128);
        int blue = 64 + Math.floorMod(seed * 7, 128);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** Terrain underlay opcodes are one-based; definition files are zero-based. */
    private static java.util.Optional<FloorDefinitionView> underlayDefinition(
            DefinitionProvider definitions, int encodedId) {
        return encodedId <= 0 ? java.util.Optional.empty() : definitions.underlay(encodedId - 1);
    }

    /** Terrain overlay values are one-based; definition files are zero-based. */
    private static java.util.Optional<FloorDefinitionView> overlayDefinition(
            DefinitionProvider definitions, int encodedId) {
        return encodedId <= 0 ? java.util.Optional.empty() : definitions.overlay(encodedId - 1);
    }
}
