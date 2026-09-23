package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureAnimationServiceTest {
    @Test
    void mapsOsrsAnimationDirectionsToNormalizedUvOffsets() {
        var left = TextureAnimationService.state(
                new TextureDefinitionView(1, false, 1, 0, 2, 4, false), 16, 128);
        var down = TextureAnimationService.state(
                new TextureDefinitionView(2, false, 2, 0, 1, 4, false), 16, 128);

        assertEquals(-0.5f, left.u(), 1e-6f);
        assertEquals(0.0f, left.v(), 1e-6f);
        assertEquals(0.0f, down.u(), 1e-6f);
        assertEquals(-0.5f, down.v(), 1e-6f);
    }

    @Test
    void sharesRuneLiteCycleWrapWithNativeAnimationPath() {
        TextureDefinitionView definition =
                new TextureDefinitionView(1, false, 1, 0, 4, 3, false);

        assertEquals(TextureAnimationService.state(definition, 1, 128),
                TextureAnimationService.state(definition, 129, 128));
        assertEquals(TextureAnimation.offset(definition, 17, 128, 128).u(),
                TextureAnimationService.state(definition, 17, 128).u(), 1e-6f);
        assertEquals(TextureAnimation.offset(definition, 17, 128, 128).v(),
                TextureAnimationService.state(definition, 17, 128).v(), 1e-6f);
    }
}
