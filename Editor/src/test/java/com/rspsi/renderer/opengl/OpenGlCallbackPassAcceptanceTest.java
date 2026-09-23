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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * Production-OpenGL acceptance for the RuneLite-style opaque then alpha pass contract.
 */
class OpenGlCallbackPassAcceptanceTest {
    private static final int SIZE = 192;
    private static final int TEXTURE_SIZE = 128;
    private static final int BLUE_TEXTURE = 31;
    private static final int RED_TEXTURE = 32;
    private static long window;

    @BeforeAll
    static void createContext() {
        if (!glfwInit()) {
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
        window = glfwCreateWindow(SIZE, SIZE, "rspsi-callback-pass-acceptance", 0L, 0L);
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
    void alphaPassCompositesAfterOpaqueAtEqualDepth() {
        try (OpenGlSceneRenderer renderer = initializedRenderer()) {
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);

            renderer.draw(plan(true, false), camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int opaqueOnly = centerRgb();

            renderer.draw(plan(false, true), camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int alphaOnly = centerRgb();

            renderer.draw(plan(true, true), camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int composed = centerRgb();

            assertNotEquals(opaqueOnly, alphaOnly,
                    "fixture colors must distinguish opaque and alpha submissions");
            assertNotEquals(opaqueOnly, composed,
                    "alpha must execute after opaque instead of being overwritten by it");
            assertNotEquals(alphaOnly, composed,
                    "alpha must blend over the opaque destination, not only the clear color");

            int opaqueRed = (opaqueOnly >>> 16) & 0xFF;
            int alphaBlue = alphaOnly & 0xFF;
            int composedRed = (composed >>> 16) & 0xFF;
            int composedBlue = composed & 0xFF;
            assertTrue(composedRed > opaqueRed,
                    "red alpha surface must contribute over the blue opaque destination");
            assertTrue(composedBlue > alphaBlue,
                    "opaque blue destination must remain visible through the alpha surface");
            assertTrue(renderer.statistics().depthWritesEnabled(),
                    "renderer must restore depth writes after the alpha pass");
            assertTrue(renderer.statistics().firstGlError() == GL_NO_ERROR,
                    "callback pass acceptance must finish without a GL error");
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

    private static GpuUploadPlan plan(boolean opaque, boolean alpha) {
        List<GpuSceneVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        WorldTileAddress tile = WorldTileAddress.of(0, 0, 0);

        if (opaque) {
            int firstVertex = vertices.size();
            addQuad(vertices, BLUE_TEXTURE, 0);
            int firstIndex = indices.size();
            addQuadIndices(indices, firstVertex);
            commands.add(new GpuDrawCommand(
                    tile, SceneLayer.Kind.GROUND_OBJECT,
                    GpuDrawCommand.SubmissionPass.OPAQUE,
                    firstIndex, 6, BLUE_TEXTURE, 0, 0, 31,
                    GpuDrawCommand.RenderMode.DEFAULT));
        }
        if (alpha) {
            int firstVertex = vertices.size();
            addQuad(vertices, RED_TEXTURE, 128);
            int firstIndex = indices.size();
            addQuadIndices(indices, firstVertex);
            commands.add(new GpuDrawCommand(
                    tile, SceneLayer.Kind.GROUND_OBJECT,
                    GpuDrawCommand.SubmissionPass.ALPHA,
                    firstIndex, 6, RED_TEXTURE, 0, 0, 32,
                    GpuDrawCommand.RenderMode.DEFAULT));
        }

        RenderTextureResource blue = solidTexture(BLUE_TEXTURE, 0xFF2040FF);
        RenderTextureResource red = solidTexture(RED_TEXTURE, 0xFFFF2020);
        return new GpuUploadPlan(vertices, indices, commands, List.of(),
                Map.of(BLUE_TEXTURE, blue, RED_TEXTURE, red),
                "callback-pass-" + opaque + "-" + alpha);
    }

    private static void addQuad(List<GpuSceneVertex> vertices, int textureId, int alpha) {
        vertices.add(vertex(-35.0f, -35.0f, 100.0f, 0.0f, 0.0f, textureId, alpha));
        vertices.add(vertex(-35.0f, 35.0f, 100.0f, 0.0f, 1.0f, textureId, alpha));
        vertices.add(vertex(35.0f, 35.0f, 100.0f, 1.0f, 1.0f, textureId, alpha));
        vertices.add(vertex(35.0f, -35.0f, 100.0f, 1.0f, 0.0f, textureId, alpha));
    }

    private static void addQuadIndices(List<Integer> indices, int firstVertex) {
        indices.add(firstVertex);
        indices.add(firstVertex + 1);
        indices.add(firstVertex + 2);
        indices.add(firstVertex);
        indices.add(firstVertex + 2);
        indices.add(firstVertex + 3);
    }

    private static GpuSceneVertex vertex(float x, float y, float z,
                                         float u, float v, int textureId, int alpha) {
        return new GpuSceneVertex(
                x, y, z,
                u, v,
                127, GpuColorEncoding.TEXTURE_LIGHTNESS,
                0,
                0, 0, 0, 0,
                textureId, alpha, 0,
                0, 0, 0, 0);
    }

    private static RenderTextureResource solidTexture(int id, int argb) {
        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        java.util.Arrays.fill(pixels, argb);
        return RenderTextureResource.from(
                id,
                new TextureDefinitionView(id, false, id, 0, 0, 0, false),
                TEXTURE_SIZE, pixels);
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
