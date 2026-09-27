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

/**
 * Tests for SceneVisibilityPolicy, now mirroring Terraini's
 * VisiblePlaneWindow.maxPlaneExclusive contract.
 */
class SceneVisibilityPolicyTest {

    // -------------------------------------------------------------------------
    // maxPlaneExclusive logic tests
    // -------------------------------------------------------------------------

    @Test
    void allHeightsVisibleReturnsFullPlaneCount() {
        SceneVisibilityPolicy policy = new SceneVisibilityPolicy(0, true, false, false,
                RoofRemovalState.disabled());
        assertEquals(4, policy.maxPlaneExclusive(4));
        assertEquals(1, policy.maxPlaneExclusive(1));
    }

    @Test
    void currentHeightZeroShowsOnlyPlaneZero() {
        SceneVisibilityPolicy policy = new SceneVisibilityPolicy(0, false, false, false,
                RoofRemovalState.disabled());
        assertEquals(1, policy.maxPlaneExclusive(4));
    }

    @Test
    void currentHeightOneShowsPlanesZeroAndOne() {
        SceneVisibilityPolicy policy = new SceneVisibilityPolicy(1, false, false, false,
                RoofRemovalState.disabled());
        assertEquals(2, policy.maxPlaneExclusive(4));
    }

    @Test
    void currentHeightThreeShowsAllFourPlanes() {
        SceneVisibilityPolicy policy = new SceneVisibilityPolicy(3, false, false, false,
                RoofRemovalState.disabled());
        assertEquals(4, policy.maxPlaneExclusive(4));
    }

    @Test
    void currentHeightClampedToMaxPlanes() {
        // currentHeight=3 with only 2 planes: clamped to planes-1=1, result=2
        SceneVisibilityPolicy policy = new SceneVisibilityPolicy(3, false, false, false,
                RoofRemovalState.disabled());
        assertEquals(2, policy.maxPlaneExclusive(2));
    }

    // -------------------------------------------------------------------------
    // includes() tile gate tests
    // -------------------------------------------------------------------------

    @Test
    void editorPolicyIncludesAllAuthoredPlanes() {
        // allHeightsVisible=true → maxPlaneExclusive=4, all planes 0-3 pass
        SceneVisibilityPolicy editor = SceneVisibilityPolicy.editor();
        for (int plane = 0; plane <= 3; plane++) {
            assertTrue(editor.includes(stubTile(plane, plane, plane)));
        }
    }

    @Test
    void atHeightZeroOnlyIncludesPlaneZero() {
        SceneVisibilityPolicy policy = SceneVisibilityPolicy.atHeight(0);
        assertTrue(policy.includes(stubTile(0, 0, 0)));
        assertTrue(!policy.includes(stubTile(1, 1, 1)));
        assertTrue(!policy.includes(stubTile(2, 2, 2)));
    }

    @Test
    void atHeightTwoIncludesPlanesZeroThroughTwo() {
        SceneVisibilityPolicy policy = SceneVisibilityPolicy.atHeight(2);
        assertTrue(policy.includes(stubTile(0, 0, 0)));
        assertTrue(policy.includes(stubTile(1, 1, 1)));
        assertTrue(policy.includes(stubTile(2, 2, 2)));
        assertTrue(!policy.includes(stubTile(3, 3, 3)));
    }

    // -------------------------------------------------------------------------
    // apply() packet filtering tests
    // -------------------------------------------------------------------------

    @Test
    void applyWithAllHeightsKeepsAllTiles() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        GpuScenePacket all = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);
        GpuScenePacket filtered = SceneVisibilityPolicy.editor().apply(all);

        assertEquals(all.tiles().size(), filtered.tiles().size());
    }

    @Test
    void applyAtHeightZeroKeepsOnlyPlaneZeroTiles() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        GpuScenePacket all = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);
        GpuScenePacket plane0Only = SceneVisibilityPolicy.atHeight(0).apply(all);

        assertTrue(plane0Only.tiles().stream().allMatch(t -> t.authoredPlane() == 0));
        assertNotEquals(all.fingerprint(), plane0Only.fingerprint());
    }

    @Test
    void applyAtHeightOneTileCountIsTwiceHeightZero() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        GpuScenePacket all = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);
        GpuScenePacket plane0 = SceneVisibilityPolicy.atHeight(0).apply(all);
        GpuScenePacket plane01 = SceneVisibilityPolicy.atHeight(1).apply(all);

        // plane0 shows only plane 0 (64*64 tiles), plane01 shows planes 0+1 (128*64=8192... but also bridge tiles)
        assertTrue(plane01.tiles().size() > plane0.tiles().size());
    }

    // -------------------------------------------------------------------------
    // Fluent builder tests
    // -------------------------------------------------------------------------

    @Test
    void withCurrentHeightUpdatesFieldOnly() {
        SceneVisibilityPolicy base = SceneVisibilityPolicy.atHeight(0);
        SceneVisibilityPolicy updated = base.withCurrentHeight(2);
        assertEquals(2, updated.currentHeight());
        assertEquals(false, updated.allHeightsVisible());
        assertEquals(false, updated.showHiddenTiles());
        assertEquals(false, updated.showEmptyTiles());
    }

    @Test
    void withAllHeightsVisibleChangesTogglesField() {
        SceneVisibilityPolicy base = SceneVisibilityPolicy.atHeight(1);
        SceneVisibilityPolicy all = base.withAllHeightsVisible(true);
        assertTrue(all.allHeightsVisible());
        assertEquals(4, all.maxPlaneExclusive(4));
    }

    @Test
    void withShowHiddenTilesSetsFlag() {
        SceneVisibilityPolicy policy = SceneVisibilityPolicy.editor().withShowHiddenTiles(true);
        assertTrue(policy.showHiddenTiles());
    }

    @Test
    void withShowEmptyTilesSetsFlag() {
        SceneVisibilityPolicy policy = SceneVisibilityPolicy.editor().withShowEmptyTiles(true);
        assertTrue(policy.showEmptyTiles());
    }

    // -------------------------------------------------------------------------
    // Bridge-deck and placeholder gate tests (Terraini/Terraforge parity)
    // -------------------------------------------------------------------------

    @Test
    void bridgeDeckAuthoredAboveIsIncludedWhenItsEffectivePlaneIsVisible() {
        // Lumbridge-style bridge: authored on plane 1, drawn on scene plane 0.
        SceneTileSnapshot bridge = texturedTile(1, 0, 100, -1, false);
        assertTrue(SceneVisibilityPolicy.atHeight(0).includes(bridge));
        // A normal plane-1 tile stays hidden at height 0.
        assertTrue(!SceneVisibilityPolicy.atHeight(0).includes(texturedTile(1, 1, 100, -1, false)));
        // At height 1 both are visible.
        assertTrue(SceneVisibilityPolicy.atHeight(1).includes(bridge));
    }

    @Test
    void placeholdersStayInThePacketAndAreGatedDownstreamInRenderConfig() {
        // Packet tiles carry authored heights/settings/objects for scene APIs,
        // so empty/hidden placeholders are always kept here; the empty/hidden
        // flags strip or retint their faces in RenderConfig instead.
        SceneTileSnapshot empty = texturedTile(0, 0, -1, -1, false);
        assertTrue(empty.terrain().orElseThrow().isEmptyPlaceholder());
        assertTrue(SceneVisibilityPolicy.editor().includes(empty));
        assertTrue(SceneVisibilityPolicy.atHeight(0).includes(empty));

        SceneTileSnapshot hidden = texturedTile(0, 0, -1, -2, true);
        assertTrue(hidden.terrain().orElseThrow().isHiddenPlaceholder());
        assertTrue(SceneVisibilityPolicy.editor().includes(hidden));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SceneTileSnapshot stubTile(int authoredPlane, int effectivePlane, int cullLevel) {
        com.rspsi.editor.model.TileCoordinate coordinate =
                new com.rspsi.editor.model.TileCoordinate(authoredPlane, 1, 1);
        return new SceneTileSnapshot(
                coordinate,
                com.rspsi.editor.model.WorldTileAddress.of(1, 1, authoredPlane),
                0,
                effectivePlane,
                authoredPlane,
                authoredPlane,
                cullLevel,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                false,
                false);
    }

    /** Snapshot carrying a single-triangle terrain packet with the given appearance markers. */
    private static SceneTileSnapshot texturedTile(int authoredPlane, int effectivePlane,
                                                  int underlayHsl, int overlayHsl,
                                                  boolean overlayHidden) {
        com.rspsi.editor.model.TileCoordinate coordinate =
                new com.rspsi.editor.model.TileCoordinate(authoredPlane, 1, 1);
        TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                java.util.List.of(
                        new TerrainRenderVertex(0, 0, 0, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, 100, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, 100, 0, 128)),
                java.util.List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, underlayHsl, overlayHsl, false, overlayHidden, overlayHsl);
        return new SceneTileSnapshot(
                coordinate,
                com.rspsi.editor.model.WorldTileAddress.of(1, 1, authoredPlane),
                0,
                effectivePlane,
                authoredPlane,
                authoredPlane,
                Math.max(0, effectivePlane),
                java.util.Optional.empty(),
                java.util.Optional.of(terrain),
                java.util.List.of(),
                java.util.List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, java.util.List.of())),
                java.util.List.of(),
                false,
                effectivePlane < authoredPlane);
    }
}
