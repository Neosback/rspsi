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
