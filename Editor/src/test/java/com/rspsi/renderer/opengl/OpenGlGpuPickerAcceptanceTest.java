package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.PickerId;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.picker.DdaScenePicker;
import com.rspsi.studio.GlFramebuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glGetError;

/** Pixel-level acceptance for the optional R32UI picker-ID pass. */
class OpenGlGpuPickerAcceptanceTest {
    private static final int SIZE = 192;
    private static long window;

    @BeforeAll
    static void createContext() {
        window = HeadlessGlContext.createOrSkip(SIZE, "rspsi-gpu-picker-acceptance");
    }

    @AfterAll
    static void destroyContext() {
        HeadlessGlContext.destroy(window);
        window = 0L;
    }

    @Test
    void gpuIdMatchesDdaReferenceAndClearPixelIsInvalid() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.setGpuPickingEnabled(true);
            GpuUploadPlan plan = centeredPlan(0, 42);

            assertFalse(renderer.pickerFramebufferAllocated(),
                    "enabling picker residency must not allocate the integer framebuffer");
            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            assertFalse(renderer.pickerFramebufferAllocated(),
                    "the picker framebuffer stays lazy until an actual read is requested");

            int expected = PickerId.encode(
                    0, 0, 0, PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));
            int gpu = renderer.pickId(plan, null, camera(), SIZE, SIZE,
                    SIZE / 2.0f, SIZE / 2.0f, null);
            assertEquals(expected, gpu);
            assertTrue(renderer.pickerFramebufferAllocated());

            DdaScenePicker reference = new DdaScenePicker();
            Optional<PickResult> resolved = reference.pickMatchingId(
                    plan, null, camera(), SIZE, SIZE,
                    SIZE / 2.0f, SIZE / 2.0f,
                    SceneCameraProjection.editorDefault(), null, gpu);
            assertTrue(resolved.isPresent());
            assertEquals(42, resolved.orElseThrow().objectId());

            assertEquals(PickerId.INVALID,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE, 4.0f, 4.0f, null),
                    "untouched picker pixels must remain the reserved zero ID");
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void fullyTransparentModelFaceDoesNotOwnPickerPixel() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.setGpuPickingEnabled(true);
            GpuUploadPlan plan = centeredPlan(0, 42, 255);

            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            assertEquals(PickerId.INVALID,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE,
                            SIZE / 2.0f, SIZE / 2.0f, null));
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void topLeftCoordinatesAreFlippedExactlyOnce() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.setGpuPickingEnabled(true);
            GpuUploadPlan plan = topTrianglePlan();
            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);

            int expected = PickerId.encode(
                    0, 0, 0, PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));
            assertEquals(expected,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE, 96.0f, 34.0f, null));
            assertEquals(PickerId.INVALID,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE, 96.0f, 158.0f, null));
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void planeRestrictionRendersOnlyTheRequestedPlane() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.setGpuPickingEnabled(true);
            GpuUploadPlan plan = overlappingPlanePlan();
            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);

            int plane0 = PickerId.encode(
                    0, 0, 0, PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));
            int plane1 = PickerId.encode(
                    1, 1, 0, PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));

            assertEquals(plane0,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE,
                            SIZE / 2.0f, SIZE / 2.0f, 0));
            assertEquals(plane1,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE,
                            SIZE / 2.0f, SIZE / 2.0f, 1));
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void pickerRemainsSingleSampleWhenPresentationUsesMsaaAndReleasesWhenDisabled() {
        glfwMakeContextCurrent(window);
        try (OpenGlSceneRenderer renderer = new OpenGlSceneRenderer();
             GlFramebuffer presentation = new GlFramebuffer()) {
            renderer.initialize();
            renderer.setCullMode(OpenGlSceneRenderer.CULL_OFF);
            renderer.setGpuPickingEnabled(true);
            presentation.setCapabilityProfile(renderer.capabilityProfile());
            presentation.resize(SIZE, SIZE, 4);

            GpuUploadPlan plan = centeredPlan(0, 42);
            presentation.bindForScene();
            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            presentation.resolve();

            int expected = PickerId.encode(
                    0, 0, 0, PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT));
            assertEquals(expected,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE,
                            SIZE / 2.0f, SIZE / 2.0f, null));
            assertTrue(renderer.pickerFramebufferAllocated());

            renderer.setGpuPickingEnabled(false);
            renderer.draw(plan, camera(), SIZE, SIZE, RenderPresentation.neutral(), 0);
            assertFalse(renderer.pickerFramebufferAllocated(),
                    "disabled picking must release its optional framebuffer storage");
            assertEquals(PickerId.INVALID,
                    renderer.pickId(plan, null, camera(), SIZE, SIZE,
                            SIZE / 2.0f, SIZE / 2.0f, null));
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static CameraState camera() {
        return new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private static GpuUploadPlan centeredPlan(int plane, int objectId) {
        return centeredPlan(plane, objectId, 0);
    }

    private static GpuUploadPlan centeredPlan(int plane, int objectId, int alpha) {
        int slot = PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT);
        List<GpuSceneVertex> vertices = List.of(
                vertex(-25.0f, -20.0f, 100.0f, plane, slot, alpha),
                vertex(0.0f, 25.0f, 100.0f, plane, slot, alpha),
                vertex(25.0f, -20.0f, 100.0f, plane, slot, alpha));
        GpuDrawCommand command = command(plane, 0, objectId);
        return new GpuUploadPlan(vertices, List.of(0, 1, 2), List.of(command),
                List.of(), Map.of(), "gpu-picker-center-" + plane);
    }

    private static GpuUploadPlan topTrianglePlan() {
        int slot = PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT);
        List<GpuSceneVertex> vertices = List.of(
                // OSRS world Y points downward, so negative Y projects toward
                // the top of the viewport with the neutral camera.
                vertex(-20.0f, -20.0f, 100.0f, 0, slot),
                vertex(0.0f, -40.0f, 100.0f, 0, slot),
                vertex(20.0f, -20.0f, 100.0f, 0, slot));
        return new GpuUploadPlan(vertices, List.of(0, 1, 2), List.of(command(0, 0, 42)),
                List.of(), Map.of(), "gpu-picker-y-flip");
    }

    private static GpuUploadPlan overlappingPlanePlan() {
        int slot = PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT);
        List<GpuSceneVertex> vertices = new ArrayList<>();
        vertices.add(vertex(-25.0f, -20.0f, 100.0f, 0, slot));
        vertices.add(vertex(0.0f, 25.0f, 100.0f, 0, slot));
        vertices.add(vertex(25.0f, -20.0f, 100.0f, 0, slot));
        vertices.add(vertex(-25.0f, -20.0f, 100.0f, 1, 1, 0, slot));
        vertices.add(vertex(0.0f, 25.0f, 100.0f, 1, 1, 0, slot));
        vertices.add(vertex(25.0f, -20.0f, 100.0f, 1, 1, 0, slot));

        List<GpuDrawCommand> commands = List.of(
                command(0, 0, 0, 42),
                command(1, 1, 3, 43));
        return new GpuUploadPlan(vertices, List.of(0, 1, 2, 3, 4, 5), commands,
                List.of(), Map.of(), "gpu-picker-planes");
    }

    private static GpuDrawCommand command(int plane, int firstIndex, int objectId) {
        return command(plane, 0, firstIndex, objectId);
    }

    private static GpuDrawCommand command(
            int plane, int tileX, int firstIndex, int objectId) {
        return new GpuDrawCommand(
                WorldTileAddress.of(tileX, 0, plane),
                SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                firstIndex, 3, -1, 6, objectId);
    }

    private static GpuSceneVertex vertex(
            float x, float y, float z, int plane, int slot) {
        return vertex(x, y, z, plane, 0, 0, slot, 0);
    }

    private static GpuSceneVertex vertex(
            float x, float y, float z, int plane, int slot, int alpha) {
        return vertex(x, y, z, plane, 0, 0, slot, alpha);
    }

    private static GpuSceneVertex vertex(
            float x, float y, float z,
            int plane, int tileX, int tileY, int slot) {
        return vertex(x, y, z, plane, tileX, tileY, slot, 0);
    }

    private static GpuSceneVertex vertex(
            float x, float y, float z,
            int plane, int tileX, int tileY, int slot, int alpha) {
        return new GpuSceneVertex(
                x, y, z,
                0.0f, 0.0f,
                32767, GpuColorEncoding.PACKED_JAGEX_HSL,
                0,
                0, 0, 0, 0,
                -1, alpha, 6,
                plane, tileX, tileY, slot);
    }
}
