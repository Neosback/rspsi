package com.rspsi.renderer.opengl;

import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * Production-OpenGL acceptance for OSRS texture animation phase and draw-state isolation.
 */
class OpenGlTextureAnimationAcceptanceTest {
    private static final int SIZE = 192;
    private static final int TEXTURE_SIZE = 128;
    private static long window;

    @BeforeAll
    static void createContext() {
        boolean initialized = glfwInit();
        if (!initialized) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("CI must provide a working GLFW/OpenGL context");
            }
            Assumptions.assumeTrue(false, "No GLFW display/context available");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(SIZE, SIZE, "rspsi-texture-animation-acceptance", 0L, 0L);
        if (window == 0L) {
            glfwTerminate();
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("CI must create an OpenGL 3.3 core context");
            }
            Assumptions.assumeTrue(false, "OpenGL 3.3 core context unavailable");
        }
        glfwMakeContextCurrent(window);
    }

    @AfterAll
    static void destroyContext() {
        if (window != 0L) {
            glfwMakeContextCurrent(window);
            GL.setCapabilities(null);
            glfwDestroyWindow(window);
            window = 0L;
        }
        glfwTerminate();
    }

    @Test
    void nativeFrameMovesAnimatedTextureAndWrapsAtRuneLiteCycle128() {
        try (OpenGlSceneRenderer renderer = initializedRenderer()) {
            GpuUploadPlan plan = twoQuadPlan();

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            byte[] cycle0 = snapshot();

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 32);
            byte[] cycle32 = snapshot();

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 128);
            byte[] cycle128 = snapshot();

            int animatedDiff = changedPixels(cycle0, cycle32, 0, SIZE / 2);
            int staticDiff = changedPixels(cycle0, cycle32, SIZE / 2, SIZE);

            assertTrue(animatedDiff > 500,
                    "animated command must visibly advance between cycles 0 and 32");
            assertEquals(0, staticDiff,
                    "a static command drawn after an animated command must not inherit its UV offset");
            assertArrayEquals(cycle0, cycle128,
                    "native texture animation must wrap to RuneLite's cycle-0 phase at cycle 128");
            assertEquals(GL_NO_ERROR, renderer.statistics().firstGlError());
        }
    }

    private static OpenGlSceneRenderer initializedRenderer() {
        glfwMakeContextCurrent(window);
        OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
        renderer.initialize();
        return renderer;
    }

    private static CameraState camera() {
        return new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private static GpuUploadPlan twoQuadPlan() {
        int animatedId = 7;
        int staticId = 8;
        List<GpuSceneVertex> vertices = List.of(
                vertex(-45.0f, -25.0f, 100.0f, 0.0f, 0.0f, animatedId),
                vertex(-45.0f, 25.0f, 100.0f, 0.0f, 1.0f, animatedId),
                vertex(-5.0f, 25.0f, 100.0f, 1.0f, 1.0f, animatedId),
                vertex(-5.0f, -25.0f, 100.0f, 1.0f, 0.0f, animatedId),
                vertex(5.0f, -25.0f, 100.0f, 0.0f, 0.0f, staticId),
                vertex(5.0f, 25.0f, 100.0f, 0.0f, 1.0f, staticId),
                vertex(45.0f, 25.0f, 100.0f, 1.0f, 1.0f, staticId),
                vertex(45.0f, -25.0f, 100.0f, 1.0f, 0.0f, staticId));

        List<Integer> indices = List.of(
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7);

        WorldTileAddress tile = WorldTileAddress.of(0, 0, 0);
        List<GpuDrawCommand> commands = List.of(
                new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 6, animatedId, 0, 42),
                new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.OPAQUE,
                        6, 6, staticId, 0, 43));

        int[] pixels = stripedTexture();
        RenderTextureResource animated = RenderTextureResource.from(
                animatedId,
                new TextureDefinitionView(animatedId, false, animatedId, 0,
                        4, 1, false),
                TEXTURE_SIZE, pixels);
        RenderTextureResource staticTexture = RenderTextureResource.from(
                staticId,
                new TextureDefinitionView(staticId, false, staticId, 0,
                        0, 0, false),
                TEXTURE_SIZE, pixels);

        return new GpuUploadPlan(vertices, indices, commands, List.of(),
                Map.of(animatedId, animated, staticId, staticTexture),
                "texture-animation-native-acceptance");
    }

    private static GpuSceneVertex vertex(float x, float y, float z,
                                         float u, float v, int textureId) {
        return new GpuSceneVertex(
                x, y, z,
                u, v,
                127, GpuColorEncoding.TEXTURE_LIGHTNESS,
                0,
                0, 0, 0, 0,
                textureId, 0, 0,
                0, 0, 0, 0);
    }

    private static int[] stripedTexture() {
        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        int[] colors = {
                0xFFFF2020,
                0xFF20FF20,
                0xFF2020FF,
                0xFFFFFF20
        };
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                pixels[y * TEXTURE_SIZE + x] = colors[x / 32];
            }
        }
        return pixels;
    }

    private static byte[] snapshot() {
        glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.capacity()];
        pixels.get(result);
        return result;
    }

    private static int changedPixels(byte[] first, byte[] second, int minX, int maxX) {
        int changed = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = minX; x < maxX; x++) {
                int offset = (y * SIZE + x) * 4;
                if (first[offset] != second[offset]
                        || first[offset + 1] != second[offset + 1]
                        || first[offset + 2] != second[offset + 2]
                        || first[offset + 3] != second[offset + 3]) {
                    changed++;
                }
            }
        }
        return changed;
    }
}
