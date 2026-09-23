package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * Pixel-level acceptance for the client-front native winding contract.
 *
 * <p>This intentionally exercises the production OpenGL renderer instead of
 * duplicating its state decisions in a unit-test projection helper. The CI
 * Foundation workflow supplies an Xvfb display, allowing Mesa/llvmpipe to
 * create the same OpenGL 3.3 core context the editor requires.</p>
 */
class OpenGlBackfaceAcceptanceTest {
    private static final int SIZE = 192;
    private static long window;

    @BeforeAll
    static void createContext() {
        window = HeadlessGlContext.createOrSkip(SIZE, "rspsi-native-facing-acceptance");
    }

    @AfterAll
    static void destroyContext() {
        HeadlessGlContext.destroy(window);
        window = 0L;
    }

    @Test
    void clientFrontRendersCcwModelAndReversedDebugRejectsIt() {
        try (OpenGlSceneRenderer renderer = initializedRenderer()) {
            GpuUploadPlan model = trianglePlan(SceneLayer.Kind.GROUND_OBJECT);

            renderer.setCullMode(OpenGlSceneRenderer.CULL_FRONT_CCW);
            renderer.draw(model, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int clientFrontPixels = changedPixels();

            renderer.setCullMode(OpenGlSceneRenderer.CULL_FRONT_CW);
            renderer.draw(model, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int reversedPixels = changedPixels();

            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.draw(model, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int twoSidedPixels = changedPixels();

            assertTrue(clientFrontPixels > 500,
                    "the accepted client-front face must produce a substantial visible triangle");
            assertEquals(0, reversedPixels,
                    "the opposite winding must reject the client-visible model face");
            assertEquals(twoSidedPixels, clientFrontPixels,
                    "client-front culling must retain exactly the face the two-sided baseline shows");
            assertEquals(GL_NO_ERROR, renderer.statistics().firstGlError());
        }
    }

    @Test
    void terrainRemainsTwoSidedAcrossBothDiagnosticWindings() {
        try (OpenGlSceneRenderer renderer = initializedRenderer()) {
            GpuUploadPlan terrain = trianglePlan(SceneLayer.Kind.TERRAIN);

            renderer.setCullMode(OpenGlSceneRenderer.CULL_FRONT_CCW);
            renderer.draw(terrain, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int ccwPixels = changedPixels();

            renderer.setCullMode(OpenGlSceneRenderer.CULL_FRONT_CW);
            renderer.draw(terrain, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            int cwPixels = changedPixels();

            assertTrue(ccwPixels > 500,
                    "terrain acceptance triangle must be visible");
            assertEquals(ccwPixels, cwPixels,
                    "terrain must be unaffected by model-facing diagnostic winding");
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

    /**
     * One deliberately asymmetric client-front triangle.
     *
     * <p>OSRS Y points down. The production vertex shader negates it before
     * projection, so these vertices arrive in the OpenGL window as CCW. The
     * same command is used for terrain to prove culling is layer-scoped.</p>
     */
    private static GpuUploadPlan trianglePlan(SceneLayer.Kind layer) {
        int alpha = layer == SceneLayer.Kind.TERRAIN ? 255 : 0;
        List<GpuSceneVertex> vertices = List.of(
                vertex(-20.0f, -20.0f, 100.0f, alpha),
                vertex(0.0f, 20.0f, 100.0f, alpha),
                vertex(20.0f, -20.0f, 100.0f, alpha));
        WorldTileAddress tile = WorldTileAddress.of(0, 0, 0);
        GpuDrawCommand command = new GpuDrawCommand(
                tile, layer, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 42);
        return new GpuUploadPlan(vertices, List.of(0, 1, 2), List.of(command),
                List.of(), Map.of(), "native-facing-" + layer.name());
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int alpha) {
        return new GpuSceneVertex(
                x, y, z,
                0.0f, 0.0f,
                32767, GpuColorEncoding.PACKED_JAGEX_HSL,
                0,
                0, 0, 0, 0,
                -1, alpha, 0,
                0, 0, 0, 0);
    }

    /** Counts pixels that differ from the clear color sampled at the corner. */
    private static int changedPixels() {
        glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        int backgroundR = pixels.get(0) & 0xFF;
        int backgroundG = pixels.get(1) & 0xFF;
        int backgroundB = pixels.get(2) & 0xFF;
        int backgroundA = pixels.get(3) & 0xFF;
        int changed = 0;
        for (int offset = 0; offset < pixels.capacity(); offset += 4) {
            if ((pixels.get(offset) & 0xFF) != backgroundR
                    || (pixels.get(offset + 1) & 0xFF) != backgroundG
                    || (pixels.get(offset + 2) & 0xFF) != backgroundB
                    || (pixels.get(offset + 3) & 0xFF) != backgroundA) {
                changed++;
            }
        }
        return changed;
    }
}
