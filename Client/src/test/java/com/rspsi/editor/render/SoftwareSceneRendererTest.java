package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SoftwareSceneRendererTest {
    @Test
    void projectsAndRasterizesAnOpaqueTriangleFromTheUploadPlan() {
        GpuSceneVertex first = vertex(-20, -20, 100, 0x1200);
        GpuSceneVertex second = vertex(20, -20, 100, 0x1200);
        GpuSceneVertex third = vertex(0, 20, 100, 0x1200);
        GpuUploadPlan plan = plan(List.of(first, second, third), Map.of());

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertEquals(1, frame.submittedTriangles());
        assertEquals(1, frame.rasterizedTriangles());
        assertNotEquals(0xFF101827, frame.pixel(50, 50));
        assertNotEquals(frame.pixel(0, 0), frame.pixel(50, 50));
    }

    @Test
    void negativeOsrsHeightProjectsAboveTheCameraPlane() {
        GpuSceneVertex first = vertex(-20, -40, 100, 0x1200);
        GpuSceneVertex second = vertex(20, -40, 100, 0x1200);
        GpuSceneVertex third = vertex(0, -10, 100, 0x1200);
        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(
                plan(List.of(first, second, third), Map.of()),
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertNotEquals(0xFF101827, frame.pixel(50, 35));
        assertEquals(0xFF101827, frame.pixel(50, 55));
    }

    @Test
    void rejectsBackFacingTrianglesUsingClientWinding() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(-20, -20, 100, 0x1200),
                vertex(20, -20, 100, 0x1200),
                vertex(0, 20, 100, 0x1200));
        GpuUploadPlan backFace = new GpuUploadPlan(vertices, List.of(0, 1, 2),
                List.of(new GpuDrawCommand(WorldTileAddress.of(0, 0, 0),
                        SceneLayer.Kind.TERRAIN, GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 3, -1, 0, -1)), List.of(), Map.of(), "back-face-test");

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(backFace,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertEquals(0, frame.rasterizedTriangles());
        assertEquals(0xFF101827, frame.pixel(50, 50));
    }

    @Test
    void samplesTextureAndAppliesTexturedFaceLightness() {
        TextureDefinitionView definition = new TextureDefinitionView(7, false, 7,
                0xFF0000, 0, 0, false);
        RenderTextureResource texture = RenderTextureResource.from(7, definition, 2,
                new int[]{0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000});
        // A textured floor's vertex colour carries the texture's own hue and
        // saturation with the tile light in the lightness slot - exactly what
        // TerrainPacketBuilder emits. A bare light here would have no hue and
        // no saturation and would resolve through the grey palette axis.
        int packed = (TextureAverageColor.packedHslFromRgb(0x62A992) & 0xFF80) | 64;
        List<GpuSceneVertex> vertices = List.of(
                texturedVertex(-20, -20, 100, packed, 7),
                texturedVertex(20, -20, 100, packed, 7),
                texturedVertex(0, 20, 100, packed, 7));
        GpuUploadPlan plan = plan(vertices, Map.of(7, texture));

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        // Client floor rule: the tile's hue and saturation with the tile light
        // scaled by the texel's own luminance (127 for the red texel).
        int texelLuminance = ((0xFF >> 1) + 0 + 0 + 0x7F) >> 2;
        int expected = OsrsTerrainColorMath.packedHslToRgb(
                (packed & 0xFF80) | ((64 * texelLuminance) >> 7), 0.6);
        assertEquals(0xFF000000 | expected, frame.pixel(50, 50));
    }

    @Test
    void treatsZeroTexturePixelsAsTransparentLikeTheClientGpuPath() {
        TextureDefinitionView definition = new TextureDefinitionView(8, false, 8,
                0, 0, 0, false);
        RenderTextureResource texture = RenderTextureResource.from(8, definition, 2,
                new int[]{0, 0, 0, 0});
        List<GpuSceneVertex> vertices = List.of(
                texturedVertex(-20, -20, 100, 128, 8),
                texturedVertex(20, -20, 100, 128, 8),
                texturedVertex(0, 20, 100, 128, 8));
        GpuUploadPlan plan = plan(vertices, Map.of(8, texture));

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertEquals(0xFF101827, frame.pixel(50, 50));
    }

    @Test
    void flatModelFacesDoNotInterpolateTheUnusedColorSentinel() {
        int flatColor = 0x1200;
        List<GpuSceneVertex> vertices = List.of(
                modelVertex(-20, -20, 100, flatColor),
                modelVertex(20, -20, 100, 0x7FFF),
                modelVertex(0, 20, 100, -1));
        GpuUploadPlan plan = new GpuUploadPlan(vertices, List.of(0, 2, 1),
                List.of(new GpuDrawCommand(WorldTileAddress.of(0, 0, 0),
                        SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 3, -1, 0, 1)), List.of(), Map.of(), "flat-model-test");

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertEquals(0xFF000000 | OsrsTerrainColorMath.packedHslToRgb(flatColor, 0.6),
                frame.pixel(50, 50));
    }

    @Test
    void higherPriorityCoplanarFaceWinsWithStableDepthBias() {
        List<GpuSceneVertex> vertices = List.of(
                modelPriorityVertex(-20, -20, 100, 0x1200, 0),
                modelPriorityVertex(20, -20, 100, 0x1200, 0),
                modelPriorityVertex(0, 20, 100, 0x1200, 0),
                modelPriorityVertex(-20, -20, 100, 0x4A38, 11),
                modelPriorityVertex(20, -20, 100, 0x4A38, 11),
                modelPriorityVertex(0, 20, 100, 0x4A38, 11));
        GpuUploadPlan plan = new GpuUploadPlan(vertices, List.of(0, 2, 1, 3, 5, 4),
                List.of(
                        new GpuDrawCommand(WorldTileAddress.of(0, 0, 0), SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1),
                        new GpuDrawCommand(WorldTileAddress.of(0, 0, 0), SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 11, 2)),
                List.of(), Map.of(), "priority-test");

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 1, 1000));

        assertEquals(0xFF000000 | OsrsTerrainColorMath.packedHslToRgb(0x4A38, 0.6),
                frame.pixel(50, 50));
    }

    @Test
    void clipsTrianglesThatCrossTheNearCameraPlane() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(-40, -40, 5, 0x1200),
                vertex(40, -40, 100, 0x1200),
                vertex(0, 40, 100, 0x1200));
        GpuUploadPlan plan = plan(vertices, Map.of());

        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan,
                new CameraState(0, 0, 0, 0, 0), 100, 100,
                new SceneCameraProjection((float) Math.toRadians(60), 10, 1000));

        assertEquals(1, frame.submittedTriangles());
        assertEquals(2, frame.rasterizedTriangles());
        assertNotEquals(0xFF101827, frame.pixel(50, 50));
    }

    private static GpuUploadPlan plan(List<GpuSceneVertex> vertices,
                                      Map<Integer, RenderTextureResource> textures) {
        GpuSceneVertex ignored = vertices.get(0);
        return new GpuUploadPlan(vertices, List.of(0, 2, 1),
                List.of(new GpuDrawCommand(WorldTileAddress.of(0, 0, 0),
                        SceneLayer.Kind.TERRAIN, GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 3, ignored.textureId(), 0, -1)),
                List.of(), textures, "software-test");
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int hsl) {
        return vertex(x, y, z, hsl, 0);
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int hsl, int priority) {
        return new GpuSceneVertex(x, y, z, 0, 0, hsl,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 255, priority, 0, 0, 0, 0);
    }

    private static GpuSceneVertex modelPriorityVertex(float x, float y, float z,
                                                       int hsl, int priority) {
        return new GpuSceneVertex(x, y, z, 0, 0, hsl,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, priority, 0, 0, 0, 0);
    }

    private static GpuSceneVertex texturedVertex(float x, float y, float z,
                                                  int lightness, int textureId) {
        return new GpuSceneVertex(x, y, z, 0, 0, lightness,
                GpuColorEncoding.TEXTURE_LIGHTNESS, 0,
                0, 0, 0, 0, textureId, 255, 0, 0, 0, 0, 0);
    }

    private static GpuSceneVertex modelVertex(float x, float y, float z, int color) {
        return new GpuSceneVertex(x, y, z, 0, 0, color,
                GpuColorEncoding.PACKED_JAGEX_HSL, 1,
                0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0);
    }
}
