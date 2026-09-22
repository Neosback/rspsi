package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneVisibilityPolicyTest {
    @Test
    void effectivePlaneSelectionKeepsBridgeProjectionButRemovesOtherAuthoredPlanes() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 2, 3).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.BRIDGE, java.util.List.of()));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);
        GpuScenePacket all = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);

        GpuScenePacket selected = new GpuScenePacketBuilder().build(
                SceneWindow.from(window), scene, SceneVisibilityPolicy.effectivePlane(0));

        assertEquals(64 * 64 + 1, selected.tiles().size());
        SceneTileSnapshot bridgeTile = selected.tiles().stream()
                .filter(SceneTileSnapshot::visibleBelow)
                .filter(tile -> tile.authoredPlane() == 1)
                .findFirst().orElseThrow();
        assertEquals(0, bridgeTile.effectivePlane());
        assertEquals(1, bridgeTile.authoredPlane());
        assertEquals(1, bridgeTile.renderLevel());
        assertEquals(0, bridgeTile.planeCullLevel());
        assertNotEquals(all.fingerprint(), selected.fingerprint());
    }

    @Test
    void clientTraversalUsesPhysicalCullLevelInsteadOfCurrentPlaneEquality() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(2, 5, 6).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.VIS_BELOW, java.util.List.of()));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);
        SceneWindow sceneWindow = SceneWindow.from(window);
        GpuScenePacketBuilder builder = new GpuScenePacketBuilder();

        GpuScenePacket effective = builder.build(
                sceneWindow, scene, SceneVisibilityPolicy.effectivePlane(0));
        GpuScenePacket client = builder.build(
                sceneWindow, scene, SceneVisibilityPolicy.clientTraversal(0));

        assertEquals(64 * 64, effective.tiles().size());
        assertEquals(64 * 64 + 1, client.tiles().size());
        SceneTileSnapshot visibleBelow = client.tiles().stream()
                .filter(tile -> tile.authoredPlane() == 2)
                .findFirst().orElseThrow();
        assertEquals(2, visibleBelow.effectivePlane());
        assertEquals(0, visibleBelow.planeCullLevel());
    }

    @Test
    void clientProjectionHonorsSceneMinimumBeforeTilePlaneSelection() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 2, 3).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.BRIDGE, java.util.List.of()));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow source = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(source);
        SceneWindow window = new SceneWindow(
                source,
                source.worldWindow().originX(),
                source.worldWindow().originY(),
                4,
                1,
                1,
                -1,
                source.regions().keySet(),
                java.util.List.of());

        GpuScenePacketBuilder builder = new GpuScenePacketBuilder();
        GpuScenePacket editor = builder.build(window, scene);
        GpuScenePacket belowMinimum = builder.build(
                window, scene, SceneVisibilityPolicy.effectivePlane(0));
        GpuScenePacket atMinimum = builder.build(
                window, scene, SceneVisibilityPolicy.effectivePlane(1));

        assertEquals(64 * 64 * 4, editor.tiles().size());
        assertTrue(belowMinimum.tiles().isEmpty());
        assertTrue(!atMinimum.tiles().isEmpty());
        assertTrue(atMinimum.tiles().stream()
                .allMatch(tile -> tile.effectivePlane() >= window.minimumRenderLevel()));
    }

    @Test
    void bridgeAndRoofFiltersAreIndependentPresentationChoices() {
        SceneTileSnapshot bridge = new SceneTileSnapshot(
                new com.rspsi.editor.model.TileCoordinate(1, 2, 3),
                com.rspsi.editor.model.WorldTileAddress.of(2, 3, 1),
                OsrsTileFlags.BRIDGE, 0, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), false, true);
        SceneTileSnapshot roof = new SceneTileSnapshot(
                new com.rspsi.editor.model.TileCoordinate(0, 4, 5),
                com.rspsi.editor.model.WorldTileAddress.of(4, 5, 0),
                OsrsTileFlags.REMOVE_ROOFS, 0, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), true, false);

        assertTrue(SceneVisibilityPolicy.editor().includes(bridge));
        assertTrue(SceneVisibilityPolicy.editor().includes(roof));
        assertTrue(!SceneVisibilityPolicy.effectivePlane(0)
                .withBridgeUpperGeometry(true).includes(bridge));
        assertTrue(!SceneVisibilityPolicy.editor().withRoofGeometry(true).includes(roof));
        assertTrue(SceneVisibilityPolicy.editor().withRoofGeometry(true).includes(bridge));
    }
}
