package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void mapsClientDirectionsToWrappedPixelUvOffsets() {
        assertEquals(new TextureAnimation.UvOffset(0.0f, -0.5f),
                offset(1, 1, 1));
        assertEquals(new TextureAnimation.UvOffset(-0.5f, 0.0f),
                offset(2, 1, 1));
        assertEquals(new TextureAnimation.UvOffset(0.0f, 0.5f),
                offset(3, 1, 1));
        assertEquals(new TextureAnimation.UvOffset(0.5f, 0.0f),
                offset(4, 1, 1));
    }

    @Test
    void invalidDirectionAndStaticTexturesHaveNoDisplacement() {
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(0, 9, 4));
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(5, 9, 4));
        assertEquals(TextureAnimation.UvOffset.ZERO, offset(4, 9, 0));
    }

    private static TextureAnimation.UvOffset offset(int direction, int speed, int cycle) {
        TextureDefinitionView definition = new TextureDefinitionView(
                1, false, 1, 0, direction, speed, false);
        RenderTextureResource texture = RenderTextureResource.from(1, definition, 2,
                new int[]{1, 2, 3, 4});
        return TextureAnimation.offset(texture, cycle);
    }
}
