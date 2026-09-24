package com.rspsi.renderer.opengl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlCapabilityProfileTest {
    @Test
    void normalizesMsaaAgainstCapturedDriverLimit() {
        OpenGlCapabilityProfile profile = profile(8, 256, 65_536, 16_384, 36);

        assertEquals(0, profile.normalizeSamples(0));
        assertEquals(0, profile.normalizeSamples(1));
        assertEquals(2, profile.normalizeSamples(3));
        assertEquals(8, profile.normalizeSamples(16));
    }

    @Test
    void validatesVanillaRendererCapacity() {
        OpenGlCapabilityProfile profile = profile(
                4, 256, 65_536, FrameUniformBuffer.BYTE_SIZE, 16);

        profile.requireVanillaBaseline();

        OpenGlCapabilityProfile tooSmallUbo = profile(
                4, 256, 65_536, FrameUniformBuffer.BYTE_SIZE - 1, 16);
        assertThrows(IllegalStateException.class, tooSmallUbo::requireVanillaBaseline);
    }

    @Test
    void rejectsTextureResourcesBeforeInvalidGlAllocation() {
        OpenGlCapabilityProfile profile = profile(4, 300, 512, 16_384, 16);

        profile.requireTextureArrayLayers(300);
        profile.requireTextureStateEntries(512);

        assertThrows(IllegalStateException.class,
                () -> profile.requireTextureArrayLayers(301));
        assertThrows(IllegalStateException.class,
                () -> profile.requireTextureStateEntries(513));
    }

    @Test
    void diagnosticSummaryIncludesFutureFeatureSelectionFlags() {
        OpenGlCapabilityProfile profile = new OpenGlCapabilityProfile(
                "Vendor", "Renderer", "4.6",
                4, 6, true,
                8, 16_384, 2048, 65_536,
                65_536, 84, 192, 16, 8, 8,
                true, 16.0f,
                true, true, true);

        String diagnostic = profile.diagnosticSummary();

        assertTrue(diagnostic.contains("arrayLayers=2048"));
        assertTrue(diagnostic.contains("anisotropy=16.0"));
        assertTrue(diagnostic.contains("bufferStorage=true"));
        assertTrue(diagnostic.contains("ssbo=true"));
        assertTrue(diagnostic.contains("imageLoadStore=true"));
    }

    private static OpenGlCapabilityProfile profile(
            int maxSamples,
            int maxArrayLayers,
            int maxTextureBufferTexels,
            int maxUniformBlockBytes,
            int maxUniformBindings) {
        return new OpenGlCapabilityProfile(
                "Vendor", "Renderer", "3.3",
                3, 3, true,
                maxSamples, 16_384, maxArrayLayers, maxTextureBufferTexels,
                maxUniformBlockBytes, maxUniformBindings,
                16, 16, 8, 8,
                false, 1.0f,
                false, false, false);
    }
}
