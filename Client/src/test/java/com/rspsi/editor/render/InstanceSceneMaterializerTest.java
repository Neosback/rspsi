package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceSceneMaterializerTest {
    @Test
    void rotatesTileCoordinatesCornersAndOverlayOrientationForAllFourChunkRotations() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 3);
        sourceDocument.tile(1, 9, 18).restore(new TileSnapshot(
                10, 20, 30, 40,
                1, 2, 5, 1, 7, List.of()));
        WorldRegion sourceRegion = new WorldRegion(0, 0, sourceDocument);
        WorldRegionWindow source = new WorldRegionWindow(
                0, 0, 1, 1, Map.of(sourceRegion.regionId(), sourceRegion));

        int[][] expectedOffsets = {
                {1, 2}, {2, 6}, {6, 5}, {5, 1}
        };
        int[][] expectedHeights = {
                {10, 20, 30, 40},
                {20, 30, 40, 10},
                {30, 40, 10, 20},
                {40, 10, 20, 30}
        };

        for (int rotation = 0; rotation < 4; rotation++) {
            InstanceChunkTemplate template = new InstanceChunkTemplate(
                    2, 3, 4,
                    1, 1, 2, rotation);
            SceneWindow window = new SceneWindow(
                    source, 3200, 3200, 3, 0, 0, -1,
                    Set.of(sourceRegion.regionId()), List.of(template));

            WorldDocument materialized =
                    new InstanceSceneMaterializer(definitions()).materialize(window);

            int x = 3 * 8 + expectedOffsets[rotation][0];
            int y = 4 * 8 + expectedOffsets[rotation][1];
            TileSnapshot tile = materialized.tile(2, x, y).snapshot();

            assertEquals(expectedHeights[rotation][0], tile.southWestHeight());
            assertEquals(expectedHeights[rotation][1], tile.southEastHeight());
            assertEquals(expectedHeights[rotation][2], tile.northEastHeight());
            assertEquals(expectedHeights[rotation][3], tile.northWestHeight());
            assertEquals(1, tile.underlayId());
            assertEquals(2, tile.overlayId());
            assertEquals(5, tile.overlayShape());
            assertEquals((1 + rotation) & 3, tile.overlayRotation());
            assertEquals(7, tile.flags());
        }
    }

    @Test
    void projectedPlaneOneBridgeFlagKeepsOrdinaryEffectivePlaneSemantics() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 2);
        sourceDocument.tile(1, 1, 2).restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.BRIDGE, List.of()));
        WorldRegion sourceRegion = new WorldRegion(0, 0, sourceDocument);
        WorldRegionWindow source = new WorldRegionWindow(
                0, 0, 1, 1, Map.of(sourceRegion.regionId(), sourceRegion));
        SceneWindow window = new SceneWindow(
                source, 3200, 3200, 2, 0, 0, -1,
                Set.of(sourceRegion.regionId()),
                List.of(new InstanceChunkTemplate(
                        1, 2, 3, 1, 0, 0, 1)));

        WorldDocument materialized =
                new InstanceSceneMaterializer(definitions()).materialize(window);
        int targetX = 2 * 8 + 2;
        int targetY = 3 * 8 + 6;

        assertEquals(OsrsTileFlags.BRIDGE,
                materialized.tile(1, targetX, targetY).snapshot().flags());
        assertEquals(0, materialized.effectivePlane(1, targetX, targetY),
                "target-plane bridge flags must flow through the normal bridge resolver");
    }

    @Test
    void rotatesMultiTileObjectAnchorUsingItsOrientedFootprint() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 1);
        WorldObject sourceObject = new WorldObject(42, 10, 1, 0, 2, 3);
        sourceDocument.tile(0, 2, 3).restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(sourceObject)));
        WorldRegion sourceRegion = new WorldRegion(0, 0, sourceDocument);
        WorldRegionWindow source = new WorldRegionWindow(
                0, 0, 1, 1, Map.of(sourceRegion.regionId(), sourceRegion));
        InstanceChunkTemplate template = new InstanceChunkTemplate(
                0, 5, 6,
                0, 0, 0, 1);
        SceneWindow window = new SceneWindow(
                source, 3200, 3200, 1, 0, 0, -1,
                Set.of(sourceRegion.regionId()), List.of(template));

        WorldDocument materialized =
                new InstanceSceneMaterializer(definitions()).materialize(window);

        // Definition is 2x3. Source orientation 1 swaps it to a 3x2
        // footprint before the chunk rotation is applied.
        int targetX = 5 * 8 + 3;
        int targetY = 6 * 8 + 3;
        List<WorldObject> objects = materialized.tile(0, targetX, targetY).snapshot().objects();

        assertEquals(1, objects.size());
        WorldObject projected = objects.get(0);
        assertEquals(42, projected.id());
        assertEquals(2, projected.rotation());
        assertEquals(targetX, projected.x());
        assertEquals(targetY, projected.y());

        // A naive point-only rotation would have placed the anchor at y=5.
        assertTrue(materialized.tile(0, 5 * 8 + 3, 6 * 8 + 5)
                .snapshot().objects().isEmpty());
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                return id == 42
                        ? Optional.of(new ObjectDefinitionView(
                                42, "large object", 2, 3, List.of(), new int[0]))
                        : Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }
}
