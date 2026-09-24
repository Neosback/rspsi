package com.rspsi.renderer.opengl.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderSourceLoaderTest {
    @Test
    void expandsSharedIncludesInVanillaSceneShader() {
        ShaderSourceLoader loader = new ShaderSourceLoader("shaders");

        ShaderSourceLoader.LoadedShader shader = loader.load("scene/vanilla.vert");

        assertTrue(shader.source().startsWith("#version 330 core"));
        assertFalse(shader.source().contains("#include"));
        assertTrue(shader.source().contains("float sceneFogAmount("));
        assertTrue(shader.source().contains("vFogAmount = sceneFogAmount("));
        assertTrue(shader.source().contains("// begin include common/fog.glsl"));
    }

    @Test
    void reportsMissingShaderResourceByResolvedPath() {
        ShaderSourceLoader loader = new ShaderSourceLoader("shaders");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> loader.load("scene/does-not-exist.vert"));

        assertTrue(failure.getMessage().contains(
                "shaders/scene/does-not-exist.vert"));
    }

    @Test
    void rejectsResourceTraversalOutsideShaderRoot() {
        ShaderSourceLoader loader = new ShaderSourceLoader("shaders");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> loader.load("../config/quartz.properties"));

        assertTrue(failure.getMessage().contains("escapes shader root"));
    }
}
