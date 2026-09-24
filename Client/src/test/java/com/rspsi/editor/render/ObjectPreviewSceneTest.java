package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPreviewSceneTest {
    private static final double QUARTER = Math.PI / 2.0;

    @Test
    void frontYawFollowsRotationAndWallEdges() {
        // Game objects face south at rotation 0 and turn clockwise with rotation.
        assertEquals(0.0, ObjectPreviewScene.frontYaw(10, 0), 1e-6);
        assertEquals(QUARTER, ObjectPreviewScene.frontYaw(10, 1), 1e-6);
        assertEquals(2 * QUARTER, ObjectPreviewScene.frontYaw(22, 2), 1e-6);
        // A rotation-0 wall is the tile's west edge, seen from inside the tile (east).
        assertEquals(-QUARTER, ObjectPreviewScene.frontYaw(0, 0), 1e-6);
        assertEquals(0.0, ObjectPreviewScene.frontYaw(4, 1), 1e-6);
    }

    @Test
    void footprintSwapsOnOddRotationsAndGridAddsAMargin() {
        ObjectPreviewScene scene = ObjectPreviewScene.build(definitions(2, 3, upright()), 5, 10, 1).orElseThrow();

        assertEquals(3, scene.footprintWidth());
        assertEquals(2, scene.footprintLength());
        assertEquals(5, scene.gridWidth());
        assertEquals(4, scene.gridLength());
    }

    @Test
    void defaultCameraLooksAtTheFrontFromSlightlyAbove() {
        ObjectPreviewScene scene = ObjectPreviewScene.build(definitions(1, 1, upright()), 5, 10, 0).orElseThrow();

        CameraState camera = scene.camera(0.0f, ObjectPreviewScene.DEFAULT_ELEVATION, 1.0f, 200, 200);

        // Object tile spans z 128..256; the front (south) camera sits below that.
        assertTrue(camera.z() < 128.0f, "camera south of the object: " + camera);
        assertEquals(192.0f, camera.x(), 0.5f);
        // RuneScape Y is negative-up: above the ground plane means y < 0.
        assertTrue(camera.y() < 0.0f, "camera above the ground: " + camera);
        assertTrue(camera.pitch() < 0.0f, "camera tilted down: " + camera);
        // Framed, not miles away: well under ten tiles for a one-tile object.
        assertTrue(128.0f - camera.z() < 1280.0f, "camera too far: " + camera);
    }

    @Test
    void renderDrawsTheModelAndAGroundGrid() {
        ObjectPreviewScene scene = ObjectPreviewScene.build(definitions(1, 1, upright()), 5, 10, 0).orElseThrow();

        int size = 96;
        int[] pixels = scene.render(ObjectPreviewScene.DEFAULT_ORBIT_YAW,
                ObjectPreviewScene.DEFAULT_ELEVATION, 1.0f, size, size);

        int background = SoftwareSceneRenderer.BACKGROUND;
        int ground = 0;
        for (int y = size * 3 / 4; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (pixels[y * size + x] != background) ground++;
            }
        }
        assertTrue(ground > size * 4, "grid should cover the lower frame, got " + ground + " pixels");
        assertNotEquals(background, pixels[(size / 2) * size + size / 2], "model at the frame centre");
    }

    @Test
    void objectWithoutModelHasNoScene() {
        assertTrue(ObjectPreviewScene.build(definitions(1, 1, null), 5, 10, 0).isEmpty());
    }

    /** A tall, wide upright quad facing south, like a door or sign. */
    private static ModelGeometryView upright() {
        return new ModelGeometryView(7,
                new int[]{-56, 0, 0, 56, 0, 0, 56, -200, 0, -56, -200, 0},
                new int[]{0, 1, 2, 0, 2, 3}, new short[]{(short) 6000, (short) 6000},
                new int[]{0, 0}, new int[]{-1, -1});
    }

    private static DefinitionProvider definitions(int width, int length, ModelGeometryView geometry) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "preview", width, length,
                        List.of(), geometry == null ? new int[0] : new int[]{7},
                        geometry == null ? new int[0] : new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(ObjectAppearanceView.empty());
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.ofNullable(geometry);
            }
        };
    }
}
