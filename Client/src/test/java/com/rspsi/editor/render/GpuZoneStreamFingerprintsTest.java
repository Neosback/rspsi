package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuZoneStreamFingerprintsTest {
    private static final List<Integer> TRIANGLE = List.of(0, 1, 2);

    @Test
    void uvChangeInvalidatesGeometryButNotVanillaShading() {
        GpuZoneStreamFingerprints first = fingerprints(vertex(0, 0, 0, 100, 1, 2, 3, 4));
        GpuZoneStreamFingerprints changed = fingerprints(vertex(0.25f, 0, 0, 100, 1, 2, 3, 4));

        assertNotEquals(first.geometry(), changed.geometry());
        assertEquals(first.vertexShading(), changed.vertexShading());
        assertEquals(first.faceShading(), changed.faceShading());
        assertEquals(first.indices(), changed.indices());
        assertEquals(first.normals(), changed.normals());
    }

    @Test
    void colorChangeInvalidatesShadingWithoutReuploadingGeometry() {
        GpuZoneStreamFingerprints first = fingerprints(vertex(0, 0, 0, 100, 1, 2, 3, 4));
        GpuZoneStreamFingerprints changed = fingerprints(vertex(0, 0, 0, 200, 1, 2, 3, 4));

        assertEquals(first.geometry(), changed.geometry());
        assertNotEquals(first.vertexShading(), changed.vertexShading());
        assertEquals(first.faceShading(), changed.faceShading());
        assertEquals(first.indices(), changed.indices());
        assertEquals(first.normals(), changed.normals());
    }

    @Test
    void alphaChangeInvalidatesFaceMetadataWithoutReuploadingVertexShading() {
        GpuZoneStreamFingerprints first = fingerprints(
                vertex(0, 0, 0, 100, 255, 0, 0, 1, 2, 3, 4));
        GpuZoneStreamFingerprints changed = fingerprints(
                vertex(0, 0, 0, 100, 128, 0, 0, 1, 2, 3, 4));

        assertEquals(first.geometry(), changed.geometry());
        assertEquals(first.vertexShading(), changed.vertexShading());
        assertNotEquals(first.faceShading(), changed.faceShading());
        assertEquals(first.indices(), changed.indices());
        assertEquals(first.normals(), changed.normals());
    }

    @Test
    void normalOnlyChangeStaysOutsideCurrentVanillaResidencyIdentity() {
        GpuZoneStreamFingerprints first = fingerprints(vertex(0, 0, 0, 100, 1, 2, 3, 4));
        GpuZoneStreamFingerprints changed = fingerprints(vertex(0, 0, 0, 100, 9, 8, 7, 6));

        assertEquals(first.geometry(), changed.geometry());
        assertEquals(first.vertexShading(), changed.vertexShading());
        assertEquals(first.faceShading(), changed.faceShading());
        assertEquals(first.indices(), changed.indices());
        assertNotEquals(first.normals(), changed.normals());
        assertEquals(first.nativeFingerprint(), changed.nativeFingerprint());
    }

    @Test
    void rejectsInconsistentFaceMetadataInsideOneTriangle() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(0, 0, 0, 100, 255, 0, 0, 1, 2, 3, 4),
                vertex(0, 1, 0, 100, 128, 0, 0, 1, 2, 3, 4),
                vertex(0, 0, 1, 100, 255, 0, 0, 1, 2, 3, 4));

        assertThrows(IllegalArgumentException.class,
                () -> GpuZoneUpload.fingerprints(vertices, TRIANGLE));
    }

    @Test
    void topologyChangeOnlyInvalidatesIndexStream() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(0, 0, 0, 100, 1, 2, 3, 4),
                vertex(0, 1, 0, 100, 1, 2, 3, 4),
                vertex(0, 0, 1, 100, 1, 2, 3, 4));

        GpuZoneStreamFingerprints first =
                GpuZoneUpload.fingerprints(vertices, List.of(0, 1, 2));
        GpuZoneStreamFingerprints changed =
                GpuZoneUpload.fingerprints(vertices, List.of(0, 2, 1));

        assertEquals(first.geometry(), changed.geometry());
        assertEquals(first.vertexShading(), changed.vertexShading());
        assertEquals(first.faceShading(), changed.faceShading());
        assertNotEquals(first.indices(), changed.indices());
        assertEquals(first.normals(), changed.normals());
    }

    private static GpuZoneStreamFingerprints fingerprints(GpuSceneVertex first) {
        List<GpuSceneVertex> vertices = List.of(
                first,
                vertex(0, 1, 0, first.encodedColor(),
                        first.alpha(), first.renderType(), first.priority(),
                        first.normalX(), first.normalY(), first.normalZ(), first.normalMagnitude()),
                vertex(0, 0, 1, first.encodedColor(),
                        first.alpha(), first.renderType(), first.priority(),
                        first.normalX(), first.normalY(), first.normalZ(), first.normalMagnitude()));
        return GpuZoneUpload.fingerprints(vertices, TRIANGLE);
    }

    private static GpuSceneVertex vertex(float u, float xOffset, float zOffset, int color,
                                         int normalX, int normalY, int normalZ,
                                         int normalMagnitude) {
        return vertex(u, xOffset, zOffset, color, 255, 0, 0,
                normalX, normalY, normalZ, normalMagnitude);
    }

    private static GpuSceneVertex vertex(float u, float xOffset, float zOffset, int color,
                                         int alpha, int renderType, int priority,
                                         int normalX, int normalY, int normalZ,
                                         int normalMagnitude) {
        return new GpuSceneVertex(
                128.0f + xOffset, 0.0f, 128.0f + zOffset,
                u, 0.5f, color, GpuColorEncoding.PACKED_JAGEX_HSL,
                renderType, normalX, normalY, normalZ, normalMagnitude,
                -1, alpha, priority,
                0, 1, 1, PickerId.terrainSlot());
    }
}
