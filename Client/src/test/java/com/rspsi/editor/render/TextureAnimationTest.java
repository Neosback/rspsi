package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextureAnimationTest {
    @Test
    void compressesPrioritiesIntoClientLikeBands() {
        assertEquals(0, GpuPriority.band(0));
        assertEquals(3, GpuPriority.band(3));
        assertEquals(4, GpuPriority.band(4));
        assertEquals(5, GpuPriority.band(7));
        assertEquals(6, GpuPriority.band(8));
        assertEquals(7, GpuPriority.band(11));
    }

    @Test
    void mapsRuneLiteAnimationDirectionsToNormalizedUvOffsets() {
        assertEquals(new TextureAnimation.UvOffset(0.0f, -0.5f),
                offset(128, 128, 1, 4, 16));
        assertEquals(new TextureAnimation.UvOffset(-0.5f, 0.0f),
                offset(128, 128, 2, 4, 16));
        assertEquals(new TextureAnimation.UvOffset(0.0f, 0.5f),
                offset(128, 128, 3, 4, 16));
        assertEquals(new TextureAnimation.UvOffset(0.5f, 0.0f),
                offset(128, 128, 4, 4, 16));
    }

    @Test
    void matchesRuneLiteGpuTickTimesSpeedOver128() {
        // RuneLite TextureManager.computeTextureAnimations returns signed
        // speed vectors and vert.glsl applies:
        // tick * textureAnim * (1 / 128), with tick=gameCycle&127.
        assertEquals(new TextureAnimation.UvOffset(0.0f, -0.375f),
                offset(128, 128, 1, 3, 16));
        assertEquals(new TextureAnimation.UvOffset(0.625f, 0.0f),
                offset(128, 128, 4, 5, 16));
    }

    @Test
    void exposesStaticPerCycleRateForGpuStateTables() {
        TextureDefinitionView definition = definition(4, 3);
        TextureAnimation.UvOffset rate = TextureAnimation.rate(definition, 128, 128);

        assertEquals(new TextureAnimation.UvOffset(3.0f / 128.0f, 0.0f), rate);
        assertEquals(new TextureAnimation.UvOffset(rate.u() * 32, rate.v() * 32),
                TextureAnimation.offset(definition, 32, 128, 128));
    }

    @Test
    void wrapsAtTheSame128CyclePhaseAsRuneLiteGpu() {
        for (int direction = 1; direction <= 4; direction++) {
            assertEquals(offset(128, 128, direction, 3, 0),
                    offset(128, 128, direction, 3, 128));
            assertEquals(offset(128, 128, direction, 3, 1),
                    offset(128, 128, direction, 3, 129));
            assertEquals(offset(128, 128, direction, 3, 127),
                    offset(128, 128, direction, 3, 255));
        }
    }

    @Test
    void preservesClientPixelSpeedFor64And128Textures() {
        // One source texel per cycle is 1/64 UV for a low-detail 64px texture
        // and 1/128 UV for the normal 128px client texture. The native array
        // upscales the former to 128px, so this also represents two uploaded
        // texels per original 64px source texel.
        assertEquals(new TextureAnimation.UvOffset(1.0f / 64.0f, 0.0f),
                offset(64, 64, 4, 1, 1));
        assertEquals(new TextureAnimation.UvOffset(1.0f / 128.0f, 0.0f),
                offset(128, 128, 4, 1, 1));
    }

    @Test
    void rectangularMetadataPathUsesAxisSpecificDimensions() {
        assertEquals(new TextureAnimation.UvOffset(-0.5f, 0.0f),
                offset(64, 128, 2, 2, 16));
        assertEquals(new TextureAnimation.UvOffset(0.0f, 0.25f),
                offset(64, 128, 3, 2, 16));
    }

    @Test
    void invalidDirectionAndStaticTexturesHaveNoDisplacement() {
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(128, 128, 0, 9, 4));
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(128, 128, 5, 9, 4));
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(128, 128, 4, 0, 4));
    }

    @Test
    void rejectsInvalidCycleOrDimensions() {
        TextureDefinitionView definition = definition(4, 1);
        assertThrows(IllegalArgumentException.class,
                () -> TextureAnimation.offset(definition, -1, 128, 128));
        assertThrows(IllegalArgumentException.class,
                () -> TextureAnimation.offset(definition, 0, 0, 128));
        assertThrows(IllegalArgumentException.class,
                () -> TextureAnimation.offset(definition, 0, 128, 0));
    }

    @Test
    void unavailableRendererTextureDoesNotAnimate() {
        TextureDefinitionView definition = definition(4, 2);
        RenderTextureResource unavailable =
                RenderTextureResource.unavailable(1, definition, "fixture");
        assertEquals(TextureAnimation.UvOffset.ZERO,
                TextureAnimation.offset(unavailable, 32));
    }

    private static TextureAnimation.UvOffset offset(int width, int height,
                                                     int direction, int speed, int cycle) {
        return TextureAnimation.offset(definition(direction, speed), cycle, width, height);
    }

    private static TextureDefinitionView definition(int direction, int speed) {
        return new TextureDefinitionView(1, false, 1, 0, direction, speed, false);
    }
}
