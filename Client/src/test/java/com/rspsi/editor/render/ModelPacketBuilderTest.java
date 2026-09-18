package com.rspsi.editor.render;

import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPacketBuilderTest {
    @Test
    void animationFrameSelectionHonorsClientLoopBackCount() {
        int[] lengths = {1, 2, 3};

        assertEquals(0, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 0));
        assertEquals(0, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 1));
        assertEquals(1, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 2));
        assertEquals(1, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 4));
        assertEquals(2, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 6));
        assertEquals(1, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 9));
        assertEquals(1, ModelPacketBuilder.animationFrameIndex(3, lengths, 2, 10));
    }

    @Test
    void selectsTypedModelAndAppliesOsrsTransformOrder() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(10, 20, 30, 40,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 1, 0, 0, 0))));

        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 256, 128, 128, 10, 20, 30,
                Map.of(100, 200), Map.of(3, 4), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0},
                new int[]{3});
        DefinitionProvider definitions = definitions(appearance, geometry);

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        assertEquals(42, packet.objectId());
        assertEquals(3, packet.vertices().size());
        assertEquals(1, packet.triangles().size());
        // Textured ModelData faces carry the client light scalar. They do not
        // multiply the source HSL face color; the texture supplies the color.
        assertEquals(76, packet.triangles().get(0).colorA());
        assertEquals(76, packet.triangles().get(0).colorB());
        assertEquals(76, packet.triangles().get(0).colorC());
        assertEquals(4, packet.triangles().get(0).textureId());
        assertTrue(packet.maxX() > packet.minX());
    }

    @Test
    void combinesMultipleModelsAndPreservesTextureMappings() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ObjectAppearanceView appearance = ObjectAppearanceView.empty();
        ModelGeometryView first = triangle(7, 100);
        ModelGeometryView second = triangle(8, 200);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{7, 8}, new int[]{10, 10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) { return Optional.of(appearance); }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(id == 7 ? first : second);
            }
        };

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        assertEquals(6, packet.vertices().size());
        assertEquals(2, packet.triangles().size());
        assertEquals(3, packet.triangles().get(1).a());
    }

    @Test
    void preservesClientSimpleTextureUvMappingPerFace() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{5},
                new int[]{0}, new int[]{0}, new int[]{0}, new int[]{0, 1, 2},
                new int[]{0}, new int[]{3}, new int[]{4}, new int[]{5}, new int[]{6},
                new int[]{1}, new int[]{8}, new int[]{-2}, new int[]{7}, null, null);
        DefinitionProvider definitions = definitions(ObjectAppearanceView.empty(), geometry);

        ModelTriangle triangle = new ModelPacketBuilder(definitions).build(document).get(0)
                .triangles().get(0);

        assertEquals(0.0f, triangle.uA(), 0.0001f);
        assertEquals(1.0f, triangle.uB(), 0.0001f);
        assertEquals(0.0f, triangle.uC(), 0.0001f);
        assertEquals(0.0f, triangle.vA(), 0.0001f);
        assertEquals(0.0f, triangle.vB(), 0.0001f);
        assertEquals(1.0f, triangle.vC(), 0.0001f);
        TextureTriangle texture = new ModelPacketBuilder(definitions).build(document).get(0)
                .textureTriangles().get(0);
        assertEquals(0, texture.renderType());
        assertEquals(3, texture.scaleX());
        assertEquals(1, texture.direction());
        assertEquals(-2, texture.translationU());
    }

    @Test
    void retainsAccumulatedClientNormalMagnitudeForSmoothFaces() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2, 0, 1, 2},
                new short[]{100, 100}, new int[]{0, 0}, new int[]{-1, -1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0);

        assertEquals(-512, packet.vertices().get(0).normalY());
    }

    @Test
    void preservesClientAlphaSentinelRenderTypes() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{-1}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0);

        assertEquals(2, packet.triangles().get(0).renderType());
        assertTrue(packet.opaqueTriangleIndices().isEmpty());
        assertTrue(packet.transparentTriangleIndices().isEmpty());
    }

    @Test
    void placesModelsAtFootprintCenterAndCarriesTerrainElevation() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(10, 20, 30, 40,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = triangle(7, 100);

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0);

        assertEquals(64, packet.vertices().get(0).x());
        assertEquals(64, packet.vertices().get(0).z());
        assertEquals(25, packet.placementHeight());
    }

    @Test
    void contoursModelVerticesAgainstTheirWorldPositionWithoutDoubleCentering() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, true, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 1, 0,
                false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{-64, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0);

        // The west vertex is at world x=0, while the object centre is at x=64.
        // Its contour delta is therefore 0-64=-64, not zero from sampling the
        // already-centred position a second time.
        assertEquals(-64, packet.vertices().get(0).y());
    }

    @Test
    void mergesMatchingWorldVerticesWhenDefinitionsEnableNormalMerging() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(43, 10, 0, 0, 1, 0))));
        ModelGeometryView first = triangle(7, 100);
        ModelGeometryView second = new ModelGeometryView(8,
                new int[]{-128, 0, 0, -64, 0, 0, -128, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, true, false, 0, 0, 16, -1, 0, false, false, false, 0);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{id == 42 ? 7 : 8}, new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) { return Optional.of(appearance); }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(id == 7 ? first : second);
            }
        };

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);

        assertEquals(2, packets.size());
        assertEquals(2, packets.get(0).vertices().get(0).normalMagnitude());
        assertEquals(2, packets.get(1).vertices().get(0).normalMagnitude());
    }

    @Test
    void doesNotRelightAlreadyLitNeighborDuringNormalMerge() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(43, 10, 0, 0, 1, 0))));
        ModelGeometryView first = triangle(7, 100);
        ModelGeometryView second = new ModelGeometryView(8,
                new int[]{-128, 0, 0, -64, 0, 0, -128, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        ObjectAppearanceView merging = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, true, false, 0, 0, 16, -1, 0, false, false, false, 0);
        ObjectAppearanceView alreadyLit = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, -1, 0, false, false, false, 0);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{id == 42 ? 7 : 8}, new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(id == 42 ? merging : alreadyLit);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(id == 7 ? first : second);
            }
        };

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);

        assertEquals(1, packets.get(0).vertices().get(0).normalMagnitude());
        assertEquals(1, packets.get(1).vertices().get(0).normalMagnitude());
    }

    @Test
    void expandsWallCornerIntoBothClientModelVariants() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 2, 0, 0, 0, 0))));
        DefinitionProvider definitions = typedDefinitions(2, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        assertEquals(6, packet.vertices().size());
        assertEquals(2, packet.triangles().size());
    }

    @Test
    void mergesNormalsAcrossTheTwoModelsOfAnLWallBeforeLighting() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 2, 0, 0, 0, 0))));
        DefinitionProvider definitions = typedDefinitions(2, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        // Both client wall variants share the footprint centre vertex. TSPS
        // merges that pair before lighting; the flattened neutral packet must
        // retain the two-face normal contribution at both copies.
        assertEquals(2, packet.vertices().get(0).normalMagnitude());
        assertEquals(2, packet.vertices().get(3).normalMagnitude());
    }

    @Test
    void flatWallFacesDoNotContaminateSmoothVertexNormals() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 0, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 128, 0, 0, 0, 128},
                new int[]{0, 1, 2, 0, 2, 3},
                new short[]{100, 100}, new int[]{0, 0}, new int[]{-1, -1},
                new int[]{0, 1}, new int[]{0, 0}, new int[0], new int[0], null, null);
        DefinitionProvider definitions = typedDefinitions(0, 7, geometry);

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        // The second face is flat (type 1) and must not add a second normal
        // contribution to the shared corner.
        assertEquals(1, packet.vertices().get(0).normalMagnitude());
        assertEquals(1, packet.vertices().get(1).normalMagnitude());
        assertEquals(1, packet.vertices().get(2).normalMagnitude());
        assertEquals(0, packet.vertices().get(3).normalMagnitude());
    }

    @Test
    void usesNormalModelTypeForDiagonalCentrePiece() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 11, 0, 0, 0, 0))));
        DefinitionProvider definitions = typedDefinitions(10, 7, triangle(7, 100));

        assertTrue(!new ModelPacketBuilder(definitions).build(document).isEmpty());
    }

    @Test
    void expandsDoubleDiagonalWallDecorationIntoTwoInsideModels() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 8, 1, 0, 0, 0))));
        DefinitionProvider definitions = typedDefinitions(4, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        assertEquals(6, packet.vertices().size());
        assertEquals(2, packet.triangles().size());
    }

    private static ModelGeometryView triangle(int id, int color) {
        return new ModelGeometryView(id,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{(short) color},
                new int[]{0}, new int[]{-1});
    }

    private static DefinitionProvider definitions(ObjectAppearanceView appearance,
                                                   ModelGeometryView geometry) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{geometry.id()}, new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) { return Optional.of(appearance); }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) { return Optional.of(geometry); }
        };
    }

    private static DefinitionProvider typedDefinitions(int type, int modelId,
                                                       ModelGeometryView geometry) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{modelId}, new int[]{type}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(ObjectAppearanceView.empty());
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };
    }
}
