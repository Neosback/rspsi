package com.rspsi.cache.verify;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsTextureSemanticFixtureTest {
    private static final int[] PIXELS = {
            0x00112233, 0x00000000, 0x80112233, 0xFF445566
    };
    private static final String PIXEL_SHA256 =
            "9488a44cecae5f7a537ce649fc097d813c5631393d76c6d2878b1aa53a83edd2";

    @Test
    void loadsAndMatchesRevisionTextureMetadataAndPixels(@TempDir Path directory) throws Exception {
        Path fixturePath = directory.resolve("textures.json");
        Files.writeString(fixturePath, """
                {
                  "formatVersion": 1,
                  "revision": 240,
                  "textureSize": 128,
                  "brightness": 0.6,
                  "textures": [
                    {
                      "id": 17,
                      "fileId": 300,
                      "transparent": true,
                      "averageRgb": 13398,
                      "lowDetail": false,
                      "animationDirection": 4,
                      "animationSpeed": 9,
                      "pixelCount": 4,
                      "pixelSha256": "%s",
                      "zeroRgbPixels": 1,
                      "partialAlphaPixels": 1
                    }
                  ]
                }
                """.formatted(PIXEL_SHA256));

        OsrsTextureSemanticFixture fixture = OsrsTextureSemanticFixture.load(fixturePath);
        OsrsTextureSemanticFixture.Comparison comparison =
                fixture.compare(definitions(), 240);

        assertTrue(comparison.matches());
        assertEquals(0, comparison.differenceCount());
        assertEquals(PIXEL_SHA256, OsrsTextureSemanticFixture.pixelSha256(PIXELS));
        assertEquals(1, OsrsTextureSemanticFixture.zeroRgbPixels(PIXELS));
        assertEquals(1, OsrsTextureSemanticFixture.partialAlphaPixels(PIXELS));
    }

    @Test
    void reportsMetadataAndPixelHashMismatches(@TempDir Path directory) throws Exception {
        Path fixturePath = directory.resolve("textures.json");
        Files.writeString(fixturePath, """
                {
                  "formatVersion": 1,
                  "revision": 240,
                  "textureSize": 128,
                  "brightness": 0.6,
                  "textures": [
                    {
                      "id": 17,
                      "fileId": 301,
                      "transparent": true,
                      "averageRgb": 13398,
                      "lowDetail": false,
                      "animationDirection": 4,
                      "animationSpeed": 9,
                      "pixelCount": 4,
                      "pixelSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "zeroRgbPixels": 1,
                      "partialAlphaPixels": 1
                    }
                  ]
                }
                """);

        OsrsTextureSemanticFixture.Comparison comparison =
                OsrsTextureSemanticFixture.load(fixturePath).compare(definitions(), 240);

        assertTrue(!comparison.matches());
        assertEquals(2, comparison.differenceCount());
        assertTrue(comparison.samples().stream().anyMatch(value -> value.contains("fileId")));
        assertTrue(comparison.samples().stream().anyMatch(value -> value.contains("pixelSha256")));
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<TextureDefinitionView> texture(int id) {
                return id == 17
                        ? Optional.of(new TextureDefinitionView(
                                17, true, 300, 13398, -1, 4, 9, false))
                        : Optional.empty();
            }

            @Override public Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
                return id == 17 && Math.abs(brightness - 0.6) < 0.0001 && textureSize == 128
                        ? Optional.of(PIXELS.clone())
                        : Optional.empty();
            }
        };
    }
}
