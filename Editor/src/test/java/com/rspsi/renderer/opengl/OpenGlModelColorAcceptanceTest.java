package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.RenderPresentation;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * Pixel-level acceptance for the packed client-lit model color path.
 */
class OpenGlModelColorAcceptanceTest {
    private static final int SIZE = 192;
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
        window = glfwCreateWindow(SIZE, SIZE, "rspsi-model-color-acceptance", 0L, 0L);
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
    void nativeModelPixelUsesTheClientPaletteForBakedLitHsl() {
        int litHsl = 0x3433;
        try (OpenGlSceneRenderer renderer = initializedRenderer()) {
            renderer.draw(trianglePlan(litHsl), camera(), SIZE, SIZE,
                    RenderPresentation.neutral(), 0);

            int actual = centerRgb();
            int expected = OsrsTerrainColorMath.packedHslToRgb(litHsl, 0.6);

            assertEquals(expected, actual,
                    "native model output must resolve the baked client HSL through the OSRS palette");
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

    private static GpuUploadPlan trianglePlan(int litHsl) {
        List<GpuSceneVertex> vertices = List.of(
                vertex(-20.0f, -20.0f, 100.0f, litHsl),
                vertex(0.0f, 20.0f, 100.0f, litHsl),
                vertex(20.0f, -20.0f, 100.0f, litHsl));
        GpuDrawCommand command = new GpuDrawCommand(
                WorldTileAddress.of(0, 0, 0),
                SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 42);
        return new GpuUploadPlan(vertices, List.of(0, 1, 2), List.of(command),
                List.of(), Map.of(), "native-model-color-" + litHsl);
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int litHsl) {
        return new GpuSceneVertex(
                x, y, z,
                0.0f, 0.0f,
                litHsl, GpuColorEncoding.PACKED_JAGEX_HSL,
                0,
                0, 0, 0, 0,
                -1, 0, 0,
                0, 0, 0, 0);
    }

    private static int centerRgb() {
        glFinish();
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        return ((pixel.get(0) & 0xFF) << 16)
                | ((pixel.get(1) & 0xFF) << 8)
                | (pixel.get(2) & 0xFF);
    }
}
