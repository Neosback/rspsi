package com.rspsi.renderer.opengl;

import org.lwjgl.opengl.GLCapabilities;

import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VENDOR;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS;
import static org.lwjgl.opengl.GL20.GL_MAX_VERTEX_ATTRIBS;
import static org.lwjgl.opengl.GL30.GL_MAJOR_VERSION;
import static org.lwjgl.opengl.GL30.GL_MAX_ARRAY_TEXTURE_LAYERS;
import static org.lwjgl.opengl.GL30.GL_MAX_COLOR_ATTACHMENTS;
import static org.lwjgl.opengl.GL30.GL_MAX_SAMPLES;
import static org.lwjgl.opengl.GL30.GL_MINOR_VERSION;
import static org.lwjgl.opengl.GL31.GL_MAX_TEXTURE_BUFFER_SIZE;
import static org.lwjgl.opengl.GL31.GL_MAX_UNIFORM_BLOCK_SIZE;
import static org.lwjgl.opengl.GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS;

/**
 * Immutable snapshot of the native OpenGL driver's capabilities and limits.
 *
 * <p>The renderer captures this once after context creation and passes it to
 * consumers instead of scattering extension checks and {@code glGet*} calls
 * throughout rendering code. OpenGL 3.3 remains the required vanilla
 * baseline; newer capabilities are recorded for deterministic future feature
 * selection rather than being enabled opportunistically.</p>
 */
public record OpenGlCapabilityProfile(
        String vendor,
        String renderer,
        String version,
        int majorVersion,
        int minorVersion,
        boolean openGl33,
        int maxSamples,
        int maxTextureSize,
        int maxArrayTextureLayers,
        int maxTextureBufferTexels,
        int maxUniformBlockBytes,
        int maxUniformBufferBindings,
        int maxCombinedTextureUnits,
        int maxVertexAttributes,
        int maxColorAttachments,
        int maxDrawBuffers,
        boolean anisotropicFiltering,
        float maxAnisotropy,
        boolean bufferStorage,
        boolean shaderStorageBuffers,
        boolean imageLoadStore
) {
    public OpenGlCapabilityProfile {
        vendor = normalize(vendor);
        renderer = normalize(renderer);
        version = normalize(version);
        if (majorVersion < 0 || minorVersion < 0
                || maxSamples < 0 || maxTextureSize < 0 || maxArrayTextureLayers < 0
                || maxTextureBufferTexels < 0 || maxUniformBlockBytes < 0
                || maxUniformBufferBindings < 0 || maxCombinedTextureUnits < 0
                || maxVertexAttributes < 0 || maxColorAttachments < 0
                || maxDrawBuffers < 0) {
            throw new IllegalArgumentException("OpenGL capability limits cannot be negative");
        }
        if (!Float.isFinite(maxAnisotropy) || maxAnisotropy < 1.0f) {
            throw new IllegalArgumentException("Maximum anisotropy must be finite and at least 1");
        }
        if (!anisotropicFiltering && maxAnisotropy != 1.0f) {
            throw new IllegalArgumentException(
                    "A driver without anisotropic filtering must report maxAnisotropy=1");
        }
    }

    public static OpenGlCapabilityProfile capture(GLCapabilities capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        boolean anisotropy = capabilities.GL_EXT_texture_filter_anisotropic;
        float maxAnisotropy = anisotropy
                ? Math.max(1.0f, glGetFloat(
                        org.lwjgl.opengl.EXTTextureFilterAnisotropic
                                .GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT))
                : 1.0f;

        return new OpenGlCapabilityProfile(
                safeGlString(GL_VENDOR),
                safeGlString(GL_RENDERER),
                safeGlString(GL_VERSION),
                glGetInteger(GL_MAJOR_VERSION),
                glGetInteger(GL_MINOR_VERSION),
                capabilities.OpenGL33,
                glGetInteger(GL_MAX_SAMPLES),
                glGetInteger(GL_MAX_TEXTURE_SIZE),
                glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS),
                glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE),
                glGetInteger(GL_MAX_UNIFORM_BLOCK_SIZE),
                glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS),
                glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
                glGetInteger(GL_MAX_VERTEX_ATTRIBS),
                glGetInteger(GL_MAX_COLOR_ATTACHMENTS),
                glGetInteger(GL_MAX_DRAW_BUFFERS),
                anisotropy,
                maxAnisotropy,
                capabilities.OpenGL44 || capabilities.GL_ARB_buffer_storage,
                capabilities.OpenGL43 || capabilities.GL_ARB_shader_storage_buffer_object,
                capabilities.OpenGL42 || capabilities.GL_ARB_shader_image_load_store);
    }

    /** Deterministically clamps a user MSAA request to a supported power of two. */
    public int normalizeSamples(int requested) {
        if (requested <= 1 || maxSamples <= 1) return 0;
        int value = Math.min(requested, maxSamples);
        return Integer.highestOneBit(value);
    }

    public void requireVanillaBaseline() {
        if (!openGl33) {
            throw new IllegalStateException(
                    "RSPSi requires an OpenGL 3.3 core context; detected " + version);
        }
        if (maxArrayTextureLayers < 1 || maxTextureBufferTexels < 1
                || maxUniformBlockBytes < FrameUniformBuffer.BYTE_SIZE
                || maxUniformBufferBindings <= FrameUniformBuffer.BINDING_POINT
                || maxCombinedTextureUnits < 3) {
            throw new IllegalStateException(
                    "OpenGL 3.3 context is missing required vanilla renderer capacity: "
                            + diagnosticSummary());
        }
    }

    public void requireTextureArrayLayers(int layers) {
        if (layers < 1) {
            throw new IllegalArgumentException("Texture-array layer count must be positive");
        }
        if (layers > maxArrayTextureLayers) {
            throw new IllegalStateException(
                    "Scene requires " + layers + " texture-array layers but the driver supports "
                            + maxArrayTextureLayers);
        }
    }

    public void requireTextureStateEntries(int entries) {
        if (entries < 1) {
            throw new IllegalArgumentException("Texture-state entry count must be positive");
        }
        if (entries > maxTextureBufferTexels) {
            throw new IllegalStateException(
                    "Scene requires " + entries + " texture-state entries but the driver supports "
                            + maxTextureBufferTexels + " texture-buffer texels");
        }
    }

    /** OpenGL 3.3 integer targets are sufficient once two draw/color attachments are available. */
    public boolean supportsPickerIdPass() {
        return openGl33 && maxColorAttachments >= 2 && maxDrawBuffers >= 2;
    }

    /** OpenGL 3.3 promotes ARB_timer_query, which is used non-blockingly by profiling. */
    public boolean supportsTimerQueries() {
        return openGl33;
    }

    /** Persistent mapped buffers are only selected when buffer-storage semantics exist. */
    public boolean supportsPersistentMapping() {
        return bufferStorage;
    }

    /**
     * Multi-draw indirect is a core OpenGL 4.3 feature. macOS remains on the
     * OpenGL 3.3/4.1 ordered multi-draw fallback while modern Windows/Linux
     * drivers can submit the same shared-arena command ranges indirectly.
     */
    public boolean supportsMultiDrawIndirect() {
        return majorVersion > 4 || (majorVersion == 4 && minorVersion >= 3);
    }

    public String diagnosticSummary() {
        return vendor + " / " + renderer + " / " + version
                + "; GL=" + majorVersion + "." + minorVersion
                + ", samples=" + maxSamples
                + ", arrayLayers=" + maxArrayTextureLayers
                + ", textureBufferTexels=" + maxTextureBufferTexels
                + ", uboBytes=" + maxUniformBlockBytes
                + ", uboBindings=" + maxUniformBufferBindings
                + ", textureUnits=" + maxCombinedTextureUnits
                + ", anisotropy=" + (anisotropicFiltering ? maxAnisotropy : "off")
                + ", timerQueries=" + supportsTimerQueries()
                + ", multiDrawIndirect=" + supportsMultiDrawIndirect()
                + ", bufferStorage=" + bufferStorage
                + ", ssbo=" + shaderStorageBuffers
                + ", imageLoadStore=" + imageLoadStore;
    }

    private static String safeGlString(int name) {
        String value = glGetString(name);
        return normalize(value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim();
    }
}
