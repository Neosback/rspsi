package com.rspsi.api.scene;

import com.rspsi.api.GameObject;
import com.rspsi.api.Perspective;
import com.rspsi.api.SceneTileModel;
import com.rspsi.api.SceneTilePaint;
import com.rspsi.api.Tile;
import com.rspsi.api.coords.LocalPoint;
import com.rspsi.api.coords.WorldPoint;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuScenePacketBuilder;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.SceneTileSnapshot;
import com.rspsi.editor.render.SceneWindow;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TerrainRenderVertex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneViewTest {
    private static final int BASE_X = 3200;
    private static final int BASE_Y = 3200;

    @Test
    void underlayAndFullOverlayBecomePaintAndShapedOverlayBecomesModel() {
        Fixture fixture = new Fixture();
        SceneView scene = fixture.scene();

        Tile underlay = scene.getTile(0, 0, 0);
        SceneTilePaint underlayPaint = underlay.getSceneTilePaint();
        assertNotNull(underlayPaint);
        assertNull(underlay.getSceneTileModel());
        assertEquals(-1, underlayPaint.getTexture());
        assertFalse(underlayPaint.isFlat(), "the client never marks underlay paint flat");
        assertEquals(fixture.cornerHsl(0, 0, 0, 0, 0), underlayPaint.getSwColor());
        assertEquals(fixture.cornerHsl(0, 0, 0, 128, 128), underlayPaint.getNeColor());

        SceneTilePaint overlayPaint = scene.getTile(0, 1, 0).getSceneTilePaint();
        assertNotNull(overlayPaint);
        assertTrue(overlayPaint.isFlat(), "full overlay on equal heights is flat");
        assertEquals(fixture.cornerHsl(0, 1, 0, 128, 0), overlayPaint.getSeColor());

        Tile shaped = scene.getTile(0, 2, 0);
        SceneTileModel model = shaped.getSceneTileModel();
        assertNull(shaped.getSceneTilePaint());
        assertNotNull(model);
        assertEquals(4, model.getShape(), "scene shape is authored shape + 1");
        assertEquals(1, model.getRotation());
        for (int x : model.getVertexX()) {
            assertTrue(x >= 256 && x <= 384, "vertex X in scene-local units of tile 2: " + x);
        }
        assertEquals(model.getFaceX().length, model.getTriangleColorA().length);
    }

    @Test
    void bridgeColumnShiftsDownAndKeepsGroundAsBridge() {
        SceneView scene = new Fixture().scene();

        Tile deck = scene.getTile(0, 1, 1);
        assertEquals(1, deck.getAuthoredPlane());
        assertEquals(0, deck.getPlane());
        assertEquals(1, deck.getRenderLevel());
        assertEquals(new WorldPoint(BASE_X + 1, BASE_Y + 1, 0), deck.getWorldLocation());

        Tile ground = deck.getBridge();
        assertNotNull(ground);
        assertEquals(0, ground.getAuthoredPlane());
        // Scene.setLinkBelow shifts the whole column: authored 2 -> scene 1,
        // authored 3 -> scene 2, and the top scene plane is left empty.
        assertEquals(2, scene.getTile(1, 1, 1).getAuthoredPlane());
        assertEquals(3, scene.getTile(2, 1, 1).getAuthoredPlane());
        assertNull(scene.getTile(3, 1, 1));
    }

    @Test
    void perspectiveTileHeightMatchesClientIntegerMathAndBridgePromotion() {
        SceneView scene = new Fixture().scene();

        // Plane 2 heights are 32*vx + 16*vy: tile (3,0) centre -> 120 exactly.
        assertEquals(120, Perspective.getTileHeight(scene, LocalPoint.fromScene(3, 0), 2));
        assertEquals(96, Perspective.getTileHeight(scene, new LocalPoint(3 * 128, 0), 2));

        // Column (1,1) is a bridge: plane 0 queries read level 1 (all 200).
        assertEquals(200, Perspective.getTileHeight(scene, LocalPoint.fromScene(1, 1), 0));
        assertEquals(0, Perspective.getTileHeight(scene, LocalPoint.fromScene(0, 0), 0));
        assertEquals(0, Perspective.getTileHeight(scene, new LocalPoint(-1, 0), 0));
    }

    @Test
    void objectsLandInRuneLiteLayersWithClientOrientation() {
        SceneView scene = new Fixture().scene();

        GameObject game = scene.getTile(0, 0, 2).getGameObjects().get(0);
        assertEquals(100, game.getId());
        assertEquals(2, game.sizeX());
        assertEquals(2, game.sizeY());
        assertEquals(new WorldPoint(BASE_X, BASE_Y + 2, 0), game.getWorldLocation());
        assertEquals(10, game.getConfig());
        assertEquals(0, game.getOrientation());
        assertTrue(scene.getTile(0, 1, 3).getGameObjects().contains(game),
                "RuneLite lists a multi-tile game object on every covered tile");

        assertEquals(1 << 1, scene.getTile(0, 3, 3).getWallObject().getOrientationA(),
                "straight wall rotation 1 faces north");
        assertEquals(102, scene.getTile(0, 2, 2).getGroundObject().getId());

        GameObject diagonal = scene.getTile(0, 3, 1).getGameObjects().get(0);
        assertEquals(768, diagonal.getOrientation(), "shape 11 rotation 1 = 512 + 256");
        assertEquals(5, scene.objects().size());
        assertTrue(game.isRendered());
    }

    @Test
    void invisibleLocsRemainSceneObjectsLikeTheClient() {
        SceneView scene = new Fixture().scene();

        var blocker = scene.getTile(0, 0, 0).getWallObject();
        assertNotNull(blocker, "an authored-empty wall is still a WallObject");
        assertEquals(104, blocker.getId());
        assertFalse(blocker.isRendered());
    }

    @Test
    void objectHashIsStableAcrossSceneRebuilds() {
        Fixture fixture = new Fixture();
        long first = fixture.scene().getTile(0, 3, 3).getWallObject().getHash();
        long second = fixture.scene().getTile(0, 3, 3).getWallObject().getHash();

        assertEquals(first, second);
    }

    @Test
    void sceneArraysUseRuneLiteEncodingsAndAreDefensiveCopies() {
        SceneView scene = new Fixture().scene();

        assertEquals(1, scene.getUnderlayIds()[0][0][0], "underlay id 0 is stored as 1");
        assertEquals(0, scene.getOverlayIds()[0][0][0]);
        assertEquals(3, scene.getTileShapes()[0][2][0]);
        assertEquals(OsrsTileFlags.BRIDGE, scene.getTileSettings()[1][1][1]);
        assertArrayEquals(new int[]{12850}, scene.getMapRegions());

        scene.getTileHeights()[2][3][0] = 999;
        assertEquals(96, scene.getTileHeight(2, 3, 0));
    }

    private static final class Fixture {
        final WorldDocument document = new WorldDocument(4, 4, 4);
        GpuScenePacket packet;

        Fixture() {
            // Plane 0: flat ground with underlays, one full overlay, one shaped overlay.
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    set(0, x, y, 0, 0, 0, 0, 1, 0, 0, 0, 0);
                    set(1, x, y, 200, 200, 200, 200, 0, 0, 0, 0, 0);
                    set(2, x, y, h(x, y), h(x + 1, y), h(x + 1, y + 1), h(x, y + 1), 0, 0, 0, 0, 0);
                }
            }
            set(0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0);
            set(0, 2, 0, 0, 0, 0, 0, 1, 1, 3, 1, 0);
            set(1, 1, 1, 200, 200, 200, 200, 1, 0, 0, 0, OsrsTileFlags.BRIDGE);

            place(new WorldObject(100, 10, 0, 0, 0, 2));
            place(new WorldObject(101, 0, 1, 0, 3, 3));
            place(new WorldObject(102, 22, 0, 0, 2, 2));
            place(new WorldObject(103, 11, 1, 0, 3, 1));
            place(new WorldObject(104, 0, 0, 0, 0, 0));
        }

        private static int h(int vx, int vy) {
            return 32 * vx + 16 * vy;
        }

        private void set(int plane, int x, int y, int sw, int se, int ne, int nw,
                         int underlay, int overlay, int shape, int rotation, int flags) {
            List<WorldObject> objects = document.tile(plane, x, y).snapshot().objects();
            document.tile(plane, x, y).restore(new TileSnapshot(sw, se, ne, nw,
                    underlay, overlay, shape, rotation, flags, objects));
        }

        private void place(WorldObject object) {
            TileSnapshot tile = document.tile(object.plane(), object.x(), object.y()).snapshot();
            document.tile(object.plane(), object.x(), object.y()).restore(new TileSnapshot(
                    tile.southWestHeight(), tile.southEastHeight(), tile.northEastHeight(),
                    tile.northWestHeight(), tile.underlayId(), tile.overlayId(), tile.overlayShape(),
                    tile.overlayRotation(), tile.flags(), List.of(object)));
        }

        SceneView scene() {
            SceneWindow window = new SceneWindow(new WorldRegionWindow(50, 50, 1, 1, Map.of()),
                    BASE_X, BASE_Y, 4, 0, Set.of(12850), List.of());
            packet = new GpuScenePacketBuilder().build(window,
                    new RenderSceneBuilder(new Definitions()).build(document));
            return SceneView.of(packet, AuthoredTileSource.of(document, BASE_X, BASE_Y), new Definitions());
        }

        int cornerHsl(int plane, int x, int y, int cornerX, int cornerY) {
            SceneTileSnapshot tile = packet.tiles().stream()
                    .filter(value -> value.worldAddress().plane() == plane
                            && value.worldAddress().worldX() == BASE_X + x
                            && value.worldAddress().worldY() == BASE_Y + y)
                    .findFirst().orElseThrow();
            TerrainRenderPacket terrain = tile.terrain().orElseThrow();
            return terrain.vertices().stream()
                    .filter(vertex -> vertex.x() == cornerX && vertex.y() == cornerY)
                    .mapToInt(TerrainRenderVertex::packedHsl)
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }
    }

    private static final class Definitions implements DefinitionProvider {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return switch (id) {
                case 100 -> Optional.of(new ObjectDefinitionView(id, "Crate", 2, 2, List.of(),
                        new int[]{7}, new int[]{10}, -1, false));
                case 101 -> Optional.of(new ObjectDefinitionView(id, "Wall", 1, 1, List.of(),
                        new int[]{7}, new int[]{0}, -1, false));
                case 102 -> Optional.of(new ObjectDefinitionView(id, "Rug", 1, 1, List.of(),
                        new int[]{7}, new int[]{22}, -1, false));
                case 103 -> Optional.of(new ObjectDefinitionView(id, "Rock", 1, 1, List.of(),
                        new int[]{7}, new int[]{10}, -1, false));
                // Invisible blocker: its only model decodes with no faces.
                case 104 -> Optional.of(new ObjectDefinitionView(id, "null", 1, 1, List.of(),
                        new int[]{8}, new int[]{0}, -1, false));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<ModelGeometryView> modelGeometry(int id) {
            if (id == 8) {
                return Optional.of(new ModelGeometryView(8, new int[]{0, 0, 0}, new int[0],
                        new short[0], new int[0], new int[0]));
            }
            return id == 7 ? Optional.of(new ModelGeometryView(7,
                    new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                    new int[]{0, 1, 2},
                    new short[]{100},
                    new int[]{0},
                    new int[]{-1})) : Optional.empty();
        }

        @Override
        public Optional<FloorDefinitionView> underlay(int id) {
            return id == 0 ? Optional.of(new FloorDefinitionView(0, -1, 0x00AA00, 48, 160, 120, 192, 256))
                    : Optional.empty();
        }

        @Override
        public Optional<FloorDefinitionView> overlay(int id) {
            return id == 0 ? Optional.of(new FloorDefinitionView(0, -1, 0xAA0000, 16, 200, 80, 64, 256))
                    : Optional.empty();
        }
    }
}
