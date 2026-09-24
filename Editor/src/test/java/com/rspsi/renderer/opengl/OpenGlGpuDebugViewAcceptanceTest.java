package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDebugView;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/** Pixel-level acceptance for diagnostic GPU presentation and optional normals. */
class OpenGlGpuDebugViewAcceptanceTest {
    private static final int SIZE = 192;
    private static long window;

    @BeforeAll
    static void createContext() {
        window = HeadlessGlContext.createOrSkip(SIZE, "rspsi-gpu-debug-view-acceptance");
    }

    @AfterAll
    static void destroyContext() {
        HeadlessGlContext.destroy(window);
        window = 0L;
    }

    @Test
    void normalViewUsesAuxiliaryStreamAndVanillaCanResumeExactly() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            GpuUploadPlan plan = trianglePlan();

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            byte[] vanilla = snapshot();

            renderer.draw(plan, camera(), SIZE, SIZE,
                    presentation(GpuDebugView.NORMALS), 0);
            byte[] normals = snapshot();
            int offset = ((SIZE / 2) * SIZE + SIZE / 2) * 4;
            int red = normals[offset] & 0xFF;
            int green = normals[offset + 1] & 0xFF;
            int blue = normals[offset + 2] & 0xFF;

            assertTrue(red > 245, "positive X normal should render near full red");
            assertTrue(green >= 120 && green <= 136,
                    "zero Y normal should map close to half green");
            assertTrue(blue >= 120 && blue <= 136,
                    "zero Z normal should map close to half blue");

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            assertArrayEquals(vanilla, snapshot(),
                    "leaving normal debug must restore the lean vanilla layout without pixel drift");
            assertEquals(GL_NO_ERROR, renderer.statistics().firstGlError());
        }
    }

    @Test
    void faceMetadataViewsRenderWithoutChangingSceneSubmission() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            GpuUploadPlan plan = trianglePlan();

            renderer.draw(plan, camera(), SIZE, SIZE,
                    presentation(GpuDebugView.PRIORITY), 0);
            byte[] priority = snapshot();

            renderer.draw(plan, camera(), SIZE, SIZE,
                    presentation(GpuDebugView.RENDER_TYPE), 0);
            byte[] renderType = snapshot();

            assertTrue(changedPixels(priority) > 500);
            assertTrue(changedPixels(renderType) > 500);
            assertTrue(differentPixels(priority, renderType) > 500,
                    "distinct face metadata views should produce distinct diagnostics");
            assertEquals(GL_NO_ERROR, renderer.statistics().firstGlError());
        }
    }

    private static RenderPresentation presentation(GpuDebugView view) {
        return new RenderPresentation(
                1.0, 0.0, false, true, 0, 0x101827, view);
    }

    private static CameraState camera() {
        return new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private static GpuUploadPlan trianglePlan() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(-25.0f, -20.0f, 100.0f),
                vertex(0.0f, 25.0f, 100.0f),
                vertex(25.0f, -20.0f, 100.0f));
        GpuDrawCommand command = new GpuDrawCommand(
                WorldTileAddress.of(0, 0, 0),
                SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 6, 42);
        return new GpuUploadPlan(vertices, List.of(0, 1, 2), List.of(command),
                List.of(), Map.of(), "gpu-debug-view-acceptance");
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(
                x, y, z,
                0.0f, 0.0f,
                32767, GpuColorEncoding.PACKED_JAGEX_HSL,
                2,
                256, 0, 0, 1,
                -1, 0, 6,
                0, 0, 0, 0);
    }

    private static byte[] snapshot() {
        glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.capacity()];
        pixels.get(result);
        return result;
    }

    private static int changedPixels(byte[] pixels) {
        int backgroundR = pixels[0] & 0xFF;
        int backgroundG = pixels[1] & 0xFF;
        int backgroundB = pixels[2] & 0xFF;
        int backgroundA = pixels[3] & 0xFF;
        int changed = 0;
        for (int offset = 0; offset < pixels.length; offset += 4) {
            if ((pixels[offset] & 0xFF) != backgroundR
                    || (pixels[offset + 1] & 0xFF) != backgroundG
                    || (pixels[offset + 2] & 0xFF) != backgroundB
                    || (pixels[offset + 3] & 0xFF) != backgroundA) {
                changed++;
            }
        }
        return changed;
    }

    private static int differentPixels(byte[] first, byte[] second) {
        int changed = 0;
        for (int offset = 0; offset < first.length; offset += 4) {
            if (first[offset] != second[offset]
                    || first[offset + 1] != second[offset + 1]
                    || first[offset + 2] != second[offset + 2]
                    || first[offset + 3] != second[offset + 3]) {
                changed++;
            }
        }
        return changed;
    }
}
