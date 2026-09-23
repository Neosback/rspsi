package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedSceneZoneTraversalTest {
    @Test
    void topLevelTraversalKeepsFiveZoneBorderAndRejectsOutsideResidency() {
        SceneWindow window = topLevelWindow(1);
        ExtendedSceneZoneTraversal traversal = new ExtendedSceneZoneTraversal(window);

        assertTrue(traversal.applies());
        assertTrue(traversal.includes(new WorldZoneCoordinate(0, 395, 400)));
        assertTrue(traversal.includes(new WorldZoneCoordinate(0, 400, 400)));
        assertTrue(traversal.includes(new WorldZoneCoordinate(0, 417, 417)));
        assertFalse(traversal.includes(new WorldZoneCoordinate(0, 394, 400)));
        assertFalse(traversal.includes(new WorldZoneCoordinate(0, 418, 400)));
    }

    @Test
    void frameClassifiesCameraInExtendedBorderWithoutChangingZoneEligibility() {
        SceneWindow window = topLevelWindow(1);
        ExtendedSceneZoneTraversal traversal = new ExtendedSceneZoneTraversal(window);
        float borderWorldUnits = 395 * ExtendedSceneZoneLayout.ZONE_SIZE * 128.0f + 64.0f;
        float normalWorldUnits = 400 * ExtendedSceneZoneLayout.ZONE_SIZE * 128.0f + 64.0f;

        ExtendedSceneZoneTraversal.Frame border = traversal.frame(
                new CameraState(borderWorldUnits, 0, normalWorldUnits, 0, 0));
        ExtendedSceneZoneTraversal.Frame normal = traversal.frame(
                new CameraState(normalWorldUnits, 0, normalWorldUnits, 0, 0));

        assertTrue(border.cameraInExtendedScene());
        assertTrue(border.cameraInBorder());
        assertEquals(new ExtendedSceneZoneLayout.Point(0, 5),
                border.cameraExtendedZone().orElseThrow());
        assertTrue(normal.cameraInExtendedScene());
        assertFalse(normal.cameraInBorder());
        assertEquals(new ExtendedSceneZoneLayout.Point(5, 5),
                normal.cameraExtendedZone().orElseThrow());

        assertTrue(border.includes(new WorldZoneCoordinate(0, 417, 417)));
        assertEquals(border.includes(new WorldZoneCoordinate(0, 417, 417)),
                normal.includes(new WorldZoneCoordinate(0, 417, 417)),
                "camera movement must not rebuild/rebase the resident extended-zone domain");
    }

    @Test
    void extendedBorderTraversalRemainsIndependentFromConnectedRoofRemoval() {
        SceneWindow window = topLevelWindow(2);
        SceneTileSnapshot lowerRoof = tile(0, 3200, 3200,
                OsrsTileFlags.REMOVE_ROOFS, 0, Optional.empty());
        SceneTileSnapshot upperRoof = tile(1, 3200, 3200,
                0, 1, Optional.of(terrain(1, 3200, 3200)));
        // Five zones west of the normal 104x104 scene: RuneLite extended-zone x=0.
        SceneTileSnapshot border = tile(0, 3160, 3200,
                0, 0, Optional.of(terrain(0, 3160, 3200)));

        GpuScenePacket source = new GpuScenePacket(
                window,
                List.of(lowerRoof, upperRoof, border),
                LightingProfile.osrs(),
                "extended-border-roof-fixture",
                Map.of());

        RoofRemovalState roofState = new RoofRemovalState(
                RoofRemovalState.POSITION,
                new RoofRemovalState.ScenePoint(0, 0),
                null, null, null, 200);
        GpuScenePacket roofFiltered = SceneVisibilityPolicy.clientTraversal(0)
                .withRoofRemovalState(roofState)
                .apply(source);

        assertFalse(roofFiltered.tiles().contains(upperRoof),
                "selected connected roof region must still remove upper geometry");
        assertTrue(roofFiltered.tiles().contains(border),
                "roof selection must not discard an independent extended-border tile");

        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(roofFiltered);
        float borderX = 3160 * 128.0f + 64.0f;
        float sceneZ = 3200 * 128.0f + 64.0f;
        GpuCommandVisibility frame = GpuCommandVisibility.of(
                plan, new CameraState(borderX, 0, sceneZ, 0, 0));

        assertEquals(window, plan.sceneWindow().orElseThrow());
        assertTrue(frame.extendedSceneApplied());
        assertTrue(frame.cameraInExtendedBorder());
        assertEquals(1, plan.commandCount());
        assertTrue(frame.visible(0),
                "the extended border remains drawable after the independent roof pass");
    }

    @Test
    void subWorldviewDoesNotApplyTopLevelFiveZoneOffset() {
        WorldRegionWindow source = new WorldRegionWindow(50, 50, 1, 1, Map.of());
        SceneWindow sub = new SceneWindow(source, 3200, 3200, 1, 0,
                0, 7, Set.of(), List.of());
        ExtendedSceneZoneTraversal traversal = new ExtendedSceneZoneTraversal(sub);

        assertFalse(traversal.applies());
        assertTrue(traversal.includes(new WorldZoneCoordinate(0, 100, 100)));
        ExtendedSceneZoneTraversal.Frame frame = traversal.frame(
                new CameraState(0, 0, 0, 0, 0));
        assertTrue(frame.cameraInExtendedScene());
        assertFalse(frame.cameraInBorder());
        assertTrue(frame.cameraExtendedZone().isEmpty());
    }

    private static SceneWindow topLevelWindow(int planes) {
        WorldRegionWindow source = new WorldRegionWindow(50, 50, 1, 1, Map.of());
        return new SceneWindow(source, 3200, 3200, planes, 0,
                0, -1, Set.of(), List.of());
    }

    private static SceneTileSnapshot tile(int plane, int x, int y, int flags,
                                          int cullLevel,
                                          Optional<TerrainRenderPacket> terrain) {
        TileCoordinate coordinate = new TileCoordinate(plane, x, y);
        return new SceneTileSnapshot(
                coordinate,
                WorldTileAddress.of(x, y, plane),
                flags,
                plane,
                plane,
                plane,
                cullLevel,
                Optional.empty(),
                terrain,
                List.of(),
                terrain.isPresent()
                        ? List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of()))
                        : List.of(),
                List.of(),
                false,
                false);
    }

    private static TerrainRenderPacket terrain(int plane, int x, int y) {
        TileCoordinate coordinate = new TileCoordinate(plane, x, y);
        return new TerrainRenderPacket(
                coordinate,
                List.of(
                        new TerrainRenderVertex(0, 0, 0, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, 100, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, 100, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
    }
}
