package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void retainsSelectedFrameHeightOffsetAndStableSceneIdentityAcrossCycles() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        WorldObject object = new WorldObject(42, 10, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(40, 40, 40, 40,
                0, 0, 0, 0, 0, List.of(object)));
        document.tile(0, 1, 0).restore(new TileSnapshot(40, 40, 40, 40,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 0, 1).restore(new TileSnapshot(40, 40, 40, 40,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 1, 1).restore(new TileSnapshot(40, 40, 40, 40,
                0, 0, 0, 0, 0, List.of()));

        ObjectAppearanceView appearance = new ObjectAppearanceView(
                77, false, 128, 128, 128, 0, 0, 0,
                Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                77, new int[]{100, 101}, new int[]{1, 1}, 2, false,
                -1, -1, 99, 0, 0, 2, -1, 6);
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(
                5, new int[]{1}, new int[][]{{1}});
        AnimationFrameView firstFrame = new AnimationFrameView(
                100, 5, new int[]{0}, new int[]{0}, new int[]{0}, new int[]{0}, false);
        AnimationFrameView secondFrame = new AnimationFrameView(
                101, 5, new int[]{0}, new int[]{10}, new int[]{0}, new int[]{0}, false);

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "animated", 1, 1,
                        List.of(), new int[]{7}, new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(appearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
            @Override public Optional<SequenceDefinitionView> sequence(int id) {
                return id == 77 ? Optional.of(sequence) : Optional.empty();
            }
            @Override public Optional<AnimationFrameView> animationFrame(int id) {
                return switch (id) {
                    case 100 -> Optional.of(firstFrame);
                    case 101 -> Optional.of(secondFrame);
                    default -> Optional.empty();
                };
            }
            @Override public Optional<SkeletonDefinitionView> skeleton(int id) {
                return id == 5 ? Optional.of(skeleton) : Optional.empty();
            }
        };

        ModelPacketBuilder builder = new ModelPacketBuilder(definitions);
        ModelRenderPacket cycleZero = builder.build(object, document, 0).orElseThrow();
        ModelRenderPacket cycleTwo = builder.build(object, document, 2).orElseThrow();

        assertEquals(cycleZero.sceneObjectIdentity(), cycleTwo.sceneObjectIdentity(),
                "frame changes must not replace the stable placed-object identity");
        assertNotEquals(cycleZero.vertices(), cycleTwo.vertices(),
                "the selected frame must change rendered geometry");

        ModelAnimationState state = cycleTwo.animationState();
        assertEquals(77, state.sequenceId());
        assertEquals(1, state.frameIndex());
        assertEquals(101, state.frameId());
        assertEquals(2, state.clientCycle());
        assertEquals(6, state.animationHeightOffset());
        assertTrue(state.transformed());
        assertEquals(40, cycleTwo.placementHeight());
        assertEquals(34, cycleTwo.renderPlacementHeight());
    }

    @Test
    void missingSelectedAnimationFrameDoesNotFallBackToAnotherSequenceFrame() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        WorldObject object = new WorldObject(42, 10, 0, 0, 0, 0);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                document.tile(0, x, y).restore(new TileSnapshot(
                        40, 40, 40, 40, 0, 0, 0, 0, 0,
                        x == 0 && y == 0 ? List.of(object) : List.of()));
            }
        }

        ObjectAppearanceView appearance = new ObjectAppearanceView(
                77, false, 128, 128, 128, 0, 0, 0,
                Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(
                7, new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                77, new int[]{100, 101}, new int[]{1, 1}, 2, false,
                -1, -1, 99, 0, 0, 2, -1, 6);
        SkeletonDefinitionView skeleton =
                new SkeletonDefinitionView(5, new int[]{1}, new int[][]{{1}});
        AnimationFrameView available = new AnimationFrameView(
                100, 5, new int[]{0}, new int[]{12},
                new int[]{0}, new int[]{0}, false);
        DefinitionProvider definitions = animatedDefinitions(
                appearance, geometry, sequence, Map.of(100, available), skeleton);

        ModelPacketBuilder builder = new ModelPacketBuilder(definitions);
        ModelRenderPacket first = builder.build(object, document, 0).orElseThrow();
        ModelRenderPacket missing = builder.build(object, document, 2).orElseThrow();

        assertEquals(100, first.animationState().frameId());
        assertTrue(first.animationState().transformed());
        assertEquals(101, missing.animationState().frameId(),
                "state must retain the frame selected by the sequence");
        assertTrue(!missing.animationState().transformed(),
                "missing selected data must not substitute a different sequence frame");
        assertNotEquals(first.vertices(), missing.vertices(),
                "the available frame transform must not leak into the missing selected frame");
        assertEquals(34, missing.renderPlacementHeight(),
                "active sequence height offset still applies when frame bytes are unavailable");
    }

    @Test
    void animationTransformsBeforeContourAndUnskewedContractKeepsAnimatedPose() {
        WorldDocument document = new WorldDocument(6, 6, 1);
        document.tile(0, 2, 2).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 2, 2))));
        document.tile(0, 3, 2).restore(new TileSnapshot(128, 128, 128, 128,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 2, 3).restore(new TileSnapshot(0, 128, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 3, 3).restore(new TileSnapshot(128, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));

        ObjectAppearanceView appearance = new ObjectAppearanceView(
                77, false, 128, 128, 128, 0, 0, 0,
                Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, 1, 0, false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 64, -128, 0, 32, -64, 48},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                77, new int[]{100}, new int[]{1}, 1, false,
                -1, -1, 99, 0, 0, 2, -1, 0);
        SkeletonDefinitionView skeleton =
                new SkeletonDefinitionView(5, new int[]{1}, new int[][]{{1}});
        AnimationFrameView frame = new AnimationFrameView(
                100, 5, new int[]{0}, new int[]{0},
                new int[]{-16}, new int[]{0}, false);

        ModelRenderPacket packet = new ModelPacketBuilder(animatedDefinitions(
                appearance, geometry, sequence, Map.of(100, frame), skeleton))
                .build(document, 0).get(0);

        assertTrue(packet.animationState().transformed());
        assertTrue(packet.contourContract().applied());
        assertEquals(List.of(-16, -144, -80),
                packet.contourContract().unskewedVertexY(),
                "HILLSKEW/unskewed state must capture the animated pose before contouring");
        assertEquals(List.of(-16, -80, -48),
                packet.vertices().stream().map(ModelVertex::y).toList(),
                "ground contour must run after the animation transform");
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
    void fullyTransparentFaceStaysOutOfBothSubmissionPasses() {
        // 0xFF is invisible in the client - its transparency is spent before the
        // write - and it arrives here as the signed byte -1.
        ModelRenderPacket packet = singleFacePacket(-1);

        assertEquals(2, packet.triangles().get(0).renderType());
        assertTrue(packet.opaqueTriangleIndices().isEmpty());
        assertTrue(packet.transparentTriangleIndices().isEmpty());
    }

    @Test
    void signedFaceTransparencyIsNormalizedInsteadOfClampedToOpaque() {
        // The cache stores one signed byte of transparency per face and the
        // client normalises it with `+= 256`, so -128 is 0x80 = half
        // transparent. Clamping it to zero is what drew translucent gate,
        // door and wall faces as solid slabs.
        ModelRenderPacket packet = singleFacePacket(-128);

        assertEquals(128, packet.triangles().get(0).alpha());
        assertTrue(packet.opaqueTriangleIndices().isEmpty());
        assertEquals(List.of(0), packet.transparentTriangleIndices());
    }

    @Test
    void nearlyInvisibleFaceKeepsItsTransparencyInsteadOfBecomingFlatColour() {
        // 0xFE (254/255 transparent) used to be promoted to render type 3 and
        // drawn as an opaque flat colour.
        ModelRenderPacket packet = singleFacePacket(-2);

        assertEquals(254, packet.triangles().get(0).alpha());
        assertEquals(0, packet.triangles().get(0).renderType());
        assertTrue(packet.opaqueTriangleIndices().isEmpty());
    }

    private static ModelRenderPacket singleFacePacket(int faceAlpha) {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{faceAlpha}, new int[]{-1});

        return new ModelPacketBuilder(definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0);
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
        // Sloped terrain with shared-corner-consistent neighbours: tile (2,2)
        // rises from sw=0 to se/ne=128. The anchor sits at (2,2) of a 6x6
        // document so the radius box plus one bilinear grid point stay
        // in-scene (the client bounds guard).
        WorldDocument document = new WorldDocument(6, 6, 1);
        document.tile(0, 2, 2).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 2, 2))));
        document.tile(0, 3, 2).restore(new TileSnapshot(128, 128, 128, 128,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 2, 3).restore(new TileSnapshot(0, 128, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 3, 3).restore(new TileSnapshot(128, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, true, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 1, 0,
                false, false, false, 0);
        // Vertical triangle; after footprint centring the vertices sit at
        // world (192,192), (256,192) and (224,240).
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, -128, 0, 32, -64, 48},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0);

        // The reference height is the placement height (tile (1,1)'s four
        // corner mean = 64). Vertex heights: 64, 128, 96. Double-centring
        // would sample the anchor corner (height 0) and produce -64 for the
        // first vertex instead of 0.
        assertEquals(0, packet.vertices().get(0).y());
        assertEquals(-64, packet.vertices().get(1).y());
        assertEquals(-32, packet.vertices().get(2).y());
    }

    /** The client leaves models whose radius box leaves the scene un-contoured. */
    @Test
    void leavesModelsExtendingPastTheSceneEdgeUncontoured() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, true, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 1, 0,
                false, false, false, 0);
        // The radius box around the anchor reaches world x=-64, past the
        // scene edge.
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{-64, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0);

        assertEquals(0, packet.vertices().get(0).y());
        assertTrue(packet.contourContract().present());
        assertTrue(!packet.contourContract().applied());
        assertEquals(ModelContourContract.Mode.FULL, packet.contourContract().mode());
        assertTrue(!packet.contourContract().hasUnskewedModel());
    }

    /**
     * Client clipType &gt; 0 partial contour: the ratio {@code (-y << 16) /
     * max(-y)} runs from 0 at the model top to 65536 at the bottom, and only
     * vertices above the clip-type parameter warp.
     */
    @Test
    void partialContourOnlyWarpsTheSpanAboveTheClipTypeParameter() {
        WorldDocument document = new WorldDocument(6, 6, 1);
        document.tile(0, 2, 2).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 2, 2))));
        document.tile(0, 3, 2).restore(new TileSnapshot(128, 128, 128, 128,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 2, 3).restore(new TileSnapshot(0, 128, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 3, 3).restore(new TileSnapshot(128, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 2, 65536,
                false, false, false, 0);
        // Vertical triangle: y=0 (top) to y=-128 (bottom); after centring the
        // vertices sit at world (192,192), (256,192) and (224,240).
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, -128, 0, 32, -64, 48},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0);

        // Reference height 64; vertex ground heights 64, 128, 96.
        // Top vertex: ratio 0 < 65536, full conform delta (64 - 64 = 0).
        assertEquals(0, packet.vertices().get(0).y());
        // Bottom vertex: ratio 65536 is not below the parameter, so it keeps
        // its model-space height (the full contour would give -64).
        assertEquals(-128, packet.vertices().get(1).y());
        // Middle vertex: ratio 32768, warp scaled by (65536-32768)/65536:
        // -64 + 32768 * 32 / 65536 = -48.
        assertEquals(-48, packet.vertices().get(2).y());
        assertEquals(ModelContourContract.Mode.PARTIAL, packet.contourContract().mode());
        assertEquals(2, packet.contourContract().type());
        assertEquals(65536, packet.contourContract().parameter());
        assertTrue(packet.contourContract().applied());
        assertEquals(List.of(0, -128, -64), packet.contourContract().unskewedVertexY());
    }

    /** clipType 0 (full contour) attaches every vertex regardless of height. */
    @Test
    void fullContourAttachesEveryVertexToTheGround() {
        WorldDocument document = new WorldDocument(6, 6, 1);
        document.tile(0, 2, 2).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 2, 2))));
        document.tile(0, 3, 2).restore(new TileSnapshot(128, 128, 128, 128,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 2, 3).restore(new TileSnapshot(0, 128, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 3, 3).restore(new TileSnapshot(128, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 1, 0,
                false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, -128, 0, 32, -64, 48},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelRenderPacket packet = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0);

        // Every vertex conforms fully to its ground height minus the 64
        // reference: 0 + 0, -128 + 64, -64 + 32.
        assertEquals(0, packet.vertices().get(0).y());
        assertEquals(-64, packet.vertices().get(1).y());
        assertEquals(-32, packet.vertices().get(2).y());
        ClientModelBounds contouredBounds = packet.clientRenderableBounds().get(0);
        assertEquals(64, contouredBounds.height(),
                "client cylinder bounds must be recalculated from the contoured model");
        assertEquals(0, contouredBounds.bottomY());
        assertTrue(packet.contourContract().present());
        assertTrue(packet.contourContract().applied());
        assertEquals(ModelContourContract.Mode.FULL, packet.contourContract().mode());
        assertEquals(1, packet.contourContract().type());
        assertEquals(0, packet.contourContract().parameter());
        assertEquals(64, packet.contourContract().placementHeight());
        assertEquals(List.of(0, -128, -64), packet.contourContract().unskewedVertexY());
        assertEquals(-128, packet.contourContract().unskewedY(1));
        assertEquals(packet.vertices().size(), packet.contourContract().metadata().vertexCount());
        List<ModelVertex> unskewed = packet.unskewedVertices().orElseThrow();
        assertEquals(List.of(0, -128, -64),
                unskewed.stream().map(ModelVertex::y).toList());
        assertEquals(packet.vertices().get(1).x(), unskewed.get(1).x());
        assertEquals(packet.vertices().get(1).z(), unskewed.get(1).z());
        assertEquals(packet.vertices().get(1).normalY(), unskewed.get(1).normalY());
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
    void straightAndDiagonalVariantsUseClientMirrorXorRule() {
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{32, 0, 16, 64, 0, 16, 32, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        WorldDocument straightDocument = new WorldDocument(1, 1, 1);
        WorldObject straight = new WorldObject(42, 0, 0, 0, 0, 0);
        straightDocument.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(straight)));

        ModelRenderPacket straightNormal = new ModelPacketBuilder(
                typedDefinitions(0, 7, geometry, appearance(false)))
                .build(straightDocument).get(0);
        ModelRenderPacket straightRotated = new ModelPacketBuilder(
                typedDefinitions(0, 7, geometry, appearance(true)))
                .build(straightDocument).get(0);

        assertEquals(96, straightNormal.vertices().get(0).x());
        assertEquals(80, straightNormal.vertices().get(0).z());
        assertEquals(96, straightRotated.vertices().get(0).x());
        assertEquals(48, straightRotated.vertices().get(0).z());
        assertEquals(1, straightNormal.triangles().get(0).b());
        assertEquals(2, straightNormal.triangles().get(0).c());
        assertEquals(2, straightRotated.triangles().get(0).b(),
                "mirroring must swap B/C so client-front winding is preserved");
        assertEquals(1, straightRotated.triangles().get(0).c());

        WorldDocument diagonalDocument = new WorldDocument(1, 1, 1);
        WorldObject diagonal = new WorldObject(42, 6, 0, 0, 0, 0);
        diagonalDocument.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(diagonal)));

        ModelRenderPacket diagonalNormal = new ModelPacketBuilder(
                typedDefinitions(4, 7, geometry, appearance(false)))
                .build(diagonalDocument).get(0);
        ModelRenderPacket diagonalRotated = new ModelPacketBuilder(
                typedDefinitions(4, 7, geometry, appearance(true)))
                .build(diagonalDocument).get(0);

        // Diagonal wall decorations pass rotation+4 into getModelData, so
        // the client's mirror condition is isRotated XOR true.
        assertEquals(128, diagonalNormal.vertices().get(0).x());
        assertEquals(-23, diagonalNormal.vertices().get(0).z());
        assertEquals(150, diagonalRotated.vertices().get(0).x());
        assertEquals(-1, diagonalRotated.vertices().get(0).z());
        assertEquals(2, diagonalNormal.triangles().get(0).b(),
                "rotation+4 mirror path must preserve front-face winding");
        assertEquals(1, diagonalNormal.triangles().get(0).c());
        assertEquals(1, diagonalRotated.triangles().get(0).b());
        assertEquals(2, diagonalRotated.triangles().get(0).c());
    }

    @Test
    void wallDecorationPacketsInheritStraightAndDiagonalWallDisplacement() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject wall = new WorldObject(100, 0, 0, 0, 0, 0);
        WorldObject straightDecoration = new WorldObject(42, 5, 0, 0, 0, 0);
        WorldObject diagonalDecoration = new WorldObject(43, 6, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0,
                List.of(wall, straightDecoration, diagonalDecoration)));
        ModelGeometryView geometry = triangle(7, 100);

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 100) {
                    return Optional.of(new ObjectDefinitionView(id, "wall", 1, 1,
                            List.of(), new int[0], new int[0], -1, false));
                }
                return Optional.of(new ObjectDefinitionView(id, "decor", 1, 1,
                        List.of(), new int[]{7}, new int[]{4}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(id == 100 ? appearanceWithDisplacement(32)
                        : ObjectAppearanceView.empty());
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);

        assertEquals(2, packets.size());
        // Shape 5 uses the full supporting wall displacement.
        assertEquals(96, packets.get(0).vertices().get(0).x());
        assertEquals(64, packets.get(0).vertices().get(0).z());
        assertEquals(new ClientRenderablePlacement(32, 0),
                packets.get(0).clientRenderablePlacements().get(0));
        // Shape 6 uses the same displacement halved on the diagonal vector.
        assertEquals(125, packets.get(1).vertices().get(0).x());
        assertEquals(3, packets.get(1).vertices().get(0).z());

        WorldDocument fallback = new WorldDocument(1, 1, 1);
        fallback.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(straightDecoration)));
        ModelRenderPacket fallbackStraight =
                new ModelPacketBuilder(definitions).build(fallback).get(0);
        assertTrue(packets.get(0).vertices().get(0).x()
                        != fallbackStraight.vertices().get(0).x(),
                "supporting-wall displacement must change scene placement");
        assertEquals(packets.get(0).clientRenderableBounds(),
                fallbackStraight.clientRenderableBounds(),
                "wall-decoration displacement is Scene placement, not Model-local bounds");
        assertEquals(new ClientRenderablePlacement(16, 0),
                fallbackStraight.clientRenderablePlacements().get(0),
                "fallback shape-5 placement must retain the client's 16-unit displacement");
    }

    @Test
    void wallDecorationDisplacementDoesNotAffectContourSampling() {
        WorldDocument document = new WorldDocument(6, 6, 1);
        WorldObject wall = new WorldObject(100, 0, 0, 0, 2, 2);
        WorldObject attached = new WorldObject(42, 4, 0, 0, 2, 2);
        WorldObject displaced = new WorldObject(43, 5, 0, 0, 2, 2);
        document.tile(0, 2, 2).restore(new TileSnapshot(0, 128, 128, 0,
                0, 0, 0, 0, 0, List.of(wall, attached, displaced)));
        document.tile(0, 3, 2).restore(new TileSnapshot(128, 128, 128, 128,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 2, 3).restore(new TileSnapshot(0, 128, 0, 0,
                0, 0, 0, 0, 0, List.of()));
        document.tile(0, 3, 3).restore(new TileSnapshot(128, 0, 0, 0,
                0, 0, 0, 0, 0, List.of()));

        ObjectAppearanceView contourAppearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, 1, 0,
                false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, -128, 0, 32, -64, 48},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 100) {
                    return Optional.of(new ObjectDefinitionView(id, "wall", 1, 1,
                            List.of(), new int[0], new int[0], -1, false));
                }
                return Optional.of(new ObjectDefinitionView(id, "decor", 1, 1,
                        List.of(), new int[]{7}, new int[]{4}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(id == 100 ? appearanceWithDisplacement(32)
                        : contourAppearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);

        assertEquals(2, packets.size());
        ModelRenderPacket attachedPacket = packets.get(0);
        ModelRenderPacket displacedPacket = packets.get(1);
        assertEquals(List.of(0, -64, -32),
                attachedPacket.vertices().stream().map(ModelVertex::y).toList());
        assertEquals(attachedPacket.vertices().stream().map(ModelVertex::y).toList(),
                displacedPacket.vertices().stream().map(ModelVertex::y).toList(),
                "Scene wall-decoration displacement must be applied after contourGround");
        assertEquals(ClientRenderablePlacement.none(),
                attachedPacket.clientRenderablePlacements().get(0));
        assertEquals(new ClientRenderablePlacement(32, 0),
                displacedPacket.clientRenderablePlacements().get(0));
        assertTrue(displacedPacket.vertices().get(0).x() != attachedPacket.vertices().get(0).x(),
                "the later Scene placement must still move the displaced decoration");
        assertTrue(attachedPacket.contourContract().applied());
        assertTrue(displacedPacket.contourContract().applied());
    }

    @Test
    void mergesNormalsAcrossTheTwoModelsOfAnLWallBeforeLighting() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 2, 0, 0, 0, 0))));
        // The client only reaches the shape-2 corner-wall normal merge for
        // objects whose definition set opcode 22 (mergeNormals). A fixture
        // without that flag (typedDefinitions' ObjectAppearanceView.empty())
        // must NOT be merged - see mergeWallVariantNormalsIsSkippedWithoutTheMergeNormalsFlag.
        DefinitionProvider definitions = typedDefinitionsWithMergeNormals(2, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        // Both client wall variants share the footprint centre vertex. TSPS
        // merges that pair before lighting; the flattened neutral packet must
        // retain the two-face normal contribution at both copies.
        assertEquals(2, packet.vertices().get(0).normalMagnitude());
        assertEquals(2, packet.vertices().get(3).normalMagnitude());
    }

    @Test
    void mergeWallVariantNormalsIsSkippedWithoutTheMergeNormalsFlag() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 2, 0, 0, 0, 0))));
        // The client only reaches Scene's shape-2 corner-wall merge for
        // objects whose definition sets opcode 22 (mergeNormals). Without
        // it, each wall piece keeps its own unmerged per-face normal.
        DefinitionProvider definitions = typedDefinitions(2, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions).build(document).get(0);

        assertEquals(1, packet.vertices().get(0).normalMagnitude());
        assertEquals(1, packet.vertices().get(3).normalMagnitude());
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
    void duplicateAuthoredPlacementsReceiveDistinctStableOccurrences() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject duplicate = new WorldObject(42, 10, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(duplicate, duplicate)));
        DefinitionProvider definitions = typedDefinitions(10, 7, triangle(7, 100));

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);

        assertEquals(2, packets.size());
        assertEquals(0, packets.get(0).sceneObjectIdentity().occurrence());
        assertEquals(1, packets.get(1).sceneObjectIdentity().occurrence());
        assertNotEquals(packets.get(0).sceneObjectIdentity(),
                packets.get(1).sceneObjectIdentity());
        assertNotEquals(packets.get(0).sceneObjectIdentity().stableId(),
                packets.get(1).sceneObjectIdentity().stableId());
    }

    @Test
    void preservesDoubleDiagonalWallDecorationAsTwoSceneRenderables() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject decoration = new WorldObject(42, 8, 1, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(decoration)));
        DefinitionProvider definitions = typedDefinitions(4, 7, triangle(7, 100));
        ModelPacketBuilder builder = new ModelPacketBuilder(definitions);

        List<ModelRenderPacket> packets = builder.build(document);

        assertEquals(2, packets.size());
        assertEquals(3, packets.get(0).vertices().size());
        assertEquals(1, packets.get(0).triangles().size());
        assertEquals(WallDecorationPresentation.Part.PRIMARY,
                packets.get(0).wallDecorationPresentation().part());
        assertEquals(1, packets.get(0).clientRenderableBounds().size());
        assertEquals(1, packets.get(1).clientRenderableBounds().size());
        assertEquals(new ClientRenderablePlacement(-8, -8),
                packets.get(0).clientRenderablePlacements().get(0));
        assertEquals(ClientRenderablePlacement.none(),
                packets.get(1).clientRenderablePlacements().get(0));
        assertTrue(packets.get(0).sceneObjectIdentity().present());
        assertEquals(packets.get(0).sceneObjectIdentity(), packets.get(1).sceneObjectIdentity(),
                "both shape-8 renderables belong to one placed scene object");
        assertEquals(-8, packets.get(0).wallDecorationPresentation().offsetX());
        assertEquals(-8, packets.get(0).wallDecorationPresentation().offsetZ());
        assertEquals(1, packets.get(0).wallDecorationPresentation().orientation());

        assertEquals(3, packets.get(1).vertices().size());
        assertEquals(1, packets.get(1).triangles().size());
        assertEquals(WallDecorationPresentation.Part.SECONDARY,
                packets.get(1).wallDecorationPresentation().part());
        assertEquals(0, packets.get(1).wallDecorationPresentation().offsetX());
        assertEquals(0, packets.get(1).wallDecorationPresentation().offsetZ());

        // The compatibility single-object API intentionally remains flattened
        // for object preview/inspection callers that predate scene ordering.
        ModelRenderPacket compatibility = builder.build(decoration, document).orElseThrow();
        assertEquals(6, compatibility.vertices().size());
        assertEquals(2, compatibility.triangles().size());
        assertEquals(WallDecorationPresentation.Part.NONE,
                compatibility.wallDecorationPresentation().part());
        assertEquals(2, compatibility.clientRenderableBounds().size(),
                "compatibility flattening must still retain both client renderable bounds");
        assertEquals(List.of(new ClientRenderablePlacement(-8, -8),
                        ClientRenderablePlacement.none()),
                compatibility.clientRenderablePlacements(),
                "compatibility flattening must retain each renderable's placement offset");
    }

    @Test
    void gameObjectSceneFootprintRotatesDefinitionDimensionsIndependentlyOfGeometryBounds() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        ModelGeometryView tinyGeometry = triangle(7, 100);
        DefinitionProvider definitions = sizedDefinitions(2, 3, 10, 7, tinyGeometry);

        for (int rotation = 0; rotation < 4; rotation++) {
            WorldObject object = new WorldObject(42, 10, rotation, 0, 2, 3);
            ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                    .build(object, document).orElseThrow();
            GameObjectSceneMetadata metadata = packet.gameObjectSceneMetadata();

            int expectedSizeX = rotation % 2 == 0 ? 2 : 3;
            int expectedSizeY = rotation % 2 == 0 ? 3 : 2;
            assertTrue(metadata.present());
            assertEquals(2, metadata.minTileX());
            assertEquals(3, metadata.minTileY());
            assertEquals(2 + expectedSizeX - 1, metadata.maxTileX());
            assertEquals(3 + expectedSizeY - 1, metadata.maxTileY());
            assertEquals(expectedSizeX, metadata.sizeX());
            assertEquals(expectedSizeY, metadata.sizeY());
            assertEquals(rotation, metadata.rotation());
            assertEquals(rotation * 512, metadata.orientation());
            assertEquals(0, metadata.modelOrientation());
            assertEquals(object.id(), packet.sceneObjectIdentity().objectId());
            assertEquals(object.type(), packet.sceneObjectIdentity().shape());
            assertEquals(object.rotation(), packet.sceneObjectIdentity().rotation());
            assertEquals(expectedSizeX, packet.sceneObjectIdentity().footprintWidth());
            assertEquals(expectedSizeY, packet.sceneObjectIdentity().footprintLength());

            // Scene occupancy is definition-driven, not inferred from this tiny model AABB.
            assertTrue(packet.maxX() - packet.minX() < expectedSizeX * 128);
        }
    }

    @Test
    void rotatedFootprintCanEndExactlyOnTheDocumentBoundary() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        DefinitionProvider definitions = sizedDefinitions(2, 3, 10, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(42, 10, 1, 0, 5, 6), document)
                .orElseThrow();
        GameObjectSceneMetadata metadata = packet.gameObjectSceneMetadata();

        assertEquals(3, metadata.sizeX());
        assertEquals(2, metadata.sizeY());
        assertEquals(5, metadata.minTileX());
        assertEquals(6, metadata.minTileY());
        assertEquals(7, metadata.maxTileX());
        assertEquals(7, metadata.maxTileY());
    }

    @Test
    void diagonalGameObjectCarriesSeparateClientModelOrientation() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        DefinitionProvider definitions = sizedDefinitions(2, 3, 10, 7, triangle(7, 100));

        for (int rotation = 0; rotation < 4; rotation++) {
            ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                    .build(new WorldObject(42, 11, rotation, 0, 1, 2), document)
                    .orElseThrow();
            GameObjectSceneMetadata metadata = packet.gameObjectSceneMetadata();

            assertTrue(metadata.present());
            assertEquals(256, metadata.modelOrientation());
            assertEquals((rotation * 512 + 256) & 2047, metadata.orientation());
            assertEquals(rotation, metadata.rotation());
        }
    }

    @Test
    void nonGameObjectLayersDoNotPretendToHaveGameObjectSceneBounds() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        ModelRenderPacket packet = new ModelPacketBuilder(
                sizedDefinitions(1, 1, 22, 7, triangle(7, 100)))
                .build(new WorldObject(42, 22, 0, 0, 0, 0), document)
                .orElseThrow();

        assertTrue(!packet.gameObjectSceneMetadata().present());
    }

    @Test
    void clientBoundsStayModelLocalAndIndependentFromSceneFootprint() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{-10, -20, -30, 50, 40, 70, 20, 10, -5},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        DefinitionProvider definitions = sizedDefinitions(2, 3, 10, 7, geometry);

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(42, 10, 0, 0, 2, 3), document)
                .orElseThrow();

        ClientModelBounds bounds = packet.clientRenderableBounds().get(0);
        assertTrue(bounds.present());
        assertEquals(20, bounds.height());
        assertEquals(40, bounds.bottomY());
        assertEquals(87, bounds.xzRadius());
        assertEquals(90, bounds.radius());
        assertEquals(186, bounds.diameter());
        assertEquals(20, bounds.drawAabb().xMid());
        assertEquals(20, bounds.drawAabb().zMid());

        // Render geometry is translated to the 2x3 footprint centre, while
        // client model bounds remain local exactly like Model + Scene.
        assertEquals(118, packet.minX());
        assertEquals(178, packet.maxX());
        assertEquals(162, packet.minZ());
        assertEquals(262, packet.maxZ());
        assertEquals(2, packet.gameObjectSceneMetadata().sizeX());
        assertEquals(3, packet.gameObjectSceneMetadata().sizeY());
    }

    @Test
    void clientBoundsIncludeDefinitionScaleAndOffsetBeforeScenePlacement() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, -20, 0, 64, 40, 0, 0, 10, 32},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 256, 64, 128, 10, -5, 20,
                Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        DefinitionProvider definitions = sizedDefinitions(1, 1, 10, 7, geometry, appearance);

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(42, 10, 0, 0, 1, 1), document)
                .orElseThrow();

        ClientModelBounds bounds = packet.clientRenderableBounds().get(0);
        assertEquals(15, bounds.height());
        assertEquals(15, bounds.bottomY());
        assertEquals(140, bounds.xzRadius());
        assertEquals(141, bounds.radius());
        assertEquals(282, bounds.diameter());
        assertEquals(69, bounds.drawAabb().xMid());
        assertEquals(0, bounds.drawAabb().yMid());
        assertEquals(26, bounds.drawAabb().zMid());
        assertEquals(69, bounds.drawAabb().xMidOffset());
        assertEquals(15, bounds.drawAabb().yMidOffset());
        assertEquals(32, bounds.drawAabb().zMidOffset());
    }

    @Test
    void lWallRetainsSeparateBoundsForItsTwoClientRenderables() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        DefinitionProvider definitions = typedDefinitions(2, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(42, 2, 0, 0, 1, 1), document)
                .orElseThrow();

        assertEquals(2, packet.clientRenderableBounds().size());
        assertTrue(packet.clientRenderableBounds().get(0).present());
        assertTrue(packet.clientRenderableBounds().get(1).present());
        assertEquals(0, packet.clientRenderableBounds().get(0).drawAabb().orientation());
        assertEquals(0, packet.clientRenderableBounds().get(1).drawAabb().orientation());
        assertTrue(packet.sceneObjectIdentity().present());
        assertEquals(2, packet.sceneObjectIdentity().shape());
    }

    @Test
    void shapeElevenCarriesClientDrawAabbOrientationSeparatelyFromPlacementRotation() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        DefinitionProvider definitions = sizedDefinitions(2, 3, 10, 7, triangle(7, 100));

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(42, 11, 3, 0, 1, 2), document)
                .orElseThrow();

        assertEquals(256, packet.clientRenderableBounds().get(0).drawAabb().orientation());
        assertEquals(256, packet.gameObjectSceneMetadata().modelOrientation());
        assertEquals(3 * 512 + 256, packet.gameObjectSceneMetadata().orientation());
    }

    private static ModelGeometryView triangle(int id, int color) {
        return new ModelGeometryView(id,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{(short) color},
                new int[]{0}, new int[]{-1});
    }

    private static DefinitionProvider animatedDefinitions(
            ObjectAppearanceView appearance,
            ModelGeometryView geometry,
            SequenceDefinitionView sequence,
            Map<Integer, AnimationFrameView> frames,
            SkeletonDefinitionView skeleton) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "animated", 1, 1,
                        List.of(), new int[]{geometry.id()}, new int[]{10}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(appearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
            @Override public Optional<SequenceDefinitionView> sequence(int id) {
                return id == sequence.id() ? Optional.of(sequence) : Optional.empty();
            }
            @Override public Optional<AnimationFrameView> animationFrame(int id) {
                return Optional.ofNullable(frames.get(id));
            }
            @Override public Optional<SkeletonDefinitionView> skeleton(int id) {
                return id == skeleton.id() ? Optional.of(skeleton) : Optional.empty();
            }
        };
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
        return typedDefinitions(type, modelId, geometry, ObjectAppearanceView.empty());
    }

    private static DefinitionProvider typedDefinitions(int type, int modelId,
                                                       ModelGeometryView geometry,
                                                       ObjectAppearanceView appearance) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{modelId}, new int[]{type}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(appearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };
    }

    private static ObjectAppearanceView appearance(boolean rotated) {
        return new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, rotated, false, 0);
    }

    private static ObjectAppearanceView appearanceWithDisplacement(int displacement) {
        return new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, false, false,
                0, 0, displacement, -1, 0, false, false, false, 0);
    }

    private static DefinitionProvider sizedDefinitions(int width, int length, int type,
                                                       int modelId, ModelGeometryView geometry) {
        return sizedDefinitions(width, length, type, modelId, geometry, ObjectAppearanceView.empty());
    }

    private static DefinitionProvider sizedDefinitions(int width, int length, int type,
                                                       int modelId, ModelGeometryView geometry,
                                                       ObjectAppearanceView appearance) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "sized", width, length,
                        List.of(), new int[]{modelId}, new int[]{type}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(appearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };
    }

    /** Like {@link #typedDefinitions}, but with opcode 22 (mergeNormals) set. */
    private static DefinitionProvider typedDefinitionsWithMergeNormals(int type, int modelId,
                                                                       ModelGeometryView geometry) {
        ObjectAppearanceView merging = new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, true, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "test", 1, 1,
                        List.of(), new int[]{modelId}, new int[]{type}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(merging);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };
    }

    @Test
    void adjacentWallsWithMergeNormalsHideInternalSeamFaces() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 0, 0, 0, 1, 1))));
        document.tile(0, 2, 1).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(43, 0, 0, 0, 2, 1))));

        ModelGeometryView geom1 = new ModelGeometryView(7,
                new int[]{128, 0, 0, 128, 50, 0, 128, 0, 50},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        ModelGeometryView geom2 = new ModelGeometryView(8,
                new int[]{0, 0, 0, 0, 50, 0, 0, 0, 50},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ObjectAppearanceView merging = new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, true, false,
                0, 0, 16, -1, 0, false, false, false, 0);

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "wall", 1, 1,
                        List.of(), new int[]{id == 42 ? 7 : 8}, new int[]{0}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(merging);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(id == 7 ? geom1 : geom2);
            }
        };

        List<ModelRenderPacket> packets = new ModelPacketBuilder(definitions).build(document);
        assertEquals(2, packets.size());
        assertEquals(2, packets.get(0).triangles().get(0).renderType());
        assertEquals(2, packets.get(1).triangles().get(0).renderType());
    }

    @Test
    void resolvesMultilocDefaultTransformWhenBaseDefinitionHasNoModels() {
        // A bare multiloc shell (Lumbridge's castle bushes are exactly this):
        // its own definition carries no models at all - the client swaps in
        // one of its varbit/varp-selected "transforms" ids, falling back to
        // multiDefault (5000 here) when no player state applies, which is
        // always the case in an editor session. Object 5001 exists only to
        // prove the resolver follows multiDefault specifically, not just the
        // first transforms entry.
        WorldDocument document = new WorldDocument(2, 2, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(10778, 10, 0, 0, 0, 0))));

        ModelGeometryView geometry = triangle(7, 100);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 10778) {
                    return Optional.of(new ObjectDefinitionView(id, null, 1, 1,
                            List.of(), new int[0], new int[0], -1, false,
                            1234, -1, new int[]{5001, 5000}, 5000));
                }
                if (id == 5000) {
                    return Optional.of(new ObjectDefinitionView(id, "Bush", 1, 1,
                            List.of(), new int[]{7}, new int[]{10}, -1, false));
                }
                return Optional.empty();
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

        Optional<ModelRenderPacket> packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(10778, 10, 0, 0, 0, 0), document);

        assertTrue(packet.isPresent(), "the multiloc shell must resolve to its default transform's model");
        assertEquals(10778, packet.get().objectId(),
                "placement identity stays the placed shell id, not the resolved transform");
    }

    @Test
    void multilocSceneFootprintUsesPlacedDefinitionSizeNotResolvedModelSize() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        ModelGeometryView geometry = triangle(7, 100);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 1000) {
                    // The placed multiloc occupies 2x3 tiles.
                    return Optional.of(new ObjectDefinitionView(id, "base", 2, 3,
                            List.of(), new int[0], new int[0], -1, false,
                            1, -1, new int[]{2000}, 2000));
                }
                if (id == 2000) {
                    // Its visible fallback model deliberately has a different size.
                    return Optional.of(new ObjectDefinitionView(id, "visible", 1, 1,
                            List.of(), new int[]{7}, new int[]{10}, -1, false));
                }
                return Optional.empty();
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

        ModelRenderPacket packet = new ModelPacketBuilder(definitions)
                .build(new WorldObject(1000, 10, 1, 0, 2, 3), document)
                .orElseThrow();

        GameObjectSceneMetadata metadata = packet.gameObjectSceneMetadata();
        assertEquals(3, metadata.sizeX());
        assertEquals(2, metadata.sizeY());
        assertEquals(2, metadata.minTileX());
        assertEquals(3, metadata.minTileY());
        assertEquals(4, metadata.maxTileX());
        assertEquals(4, metadata.maxTileY());
    }

    @Test
    void recolorBecomesUnlitFaceColorBeforeClientLighting() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0,
                Map.of(100, 200), Map.of(), true, false, false, false,
                0, 0, 16, -1, 0, false, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});

        ModelTriangle face = new ModelPacketBuilder(definitions(appearance, geometry))
                .build(document).get(0).triangles().get(0);

        // Recolor happens on ModelData before toModel/light. RuneLite's
        // unlit face-color view therefore exposes the replacement HSL.
        assertEquals(200, face.unlitColor());
        // This fixture's smooth normal produces client light 76 at each
        // vertex. method5263(200, 76) = 170.
        assertEquals(170, face.colorA());
        assertEquals(170, face.colorB());
        assertEquals(170, face.colorC());
    }

    @Test
    void flatFaceRetainsExactClientColorSlotsBeforeGpuExpansion() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{-1},
                new int[]{1},
                new int[0],
                new int[0],
                new int[0],
                null, null);

        ModelTriangle face = new ModelPacketBuilder(
                definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0).triangles().get(0);

        assertEquals(100, face.unlitColor());
        assertEquals(0, face.colorB(),
                "ModelData.toModel leaves faceColors2 zero for flat faces");
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, face.colorC());
        assertTrue(face.flatShaded());
    }


    @Test
    void texturedFlatFaceKeepsClientZeroSecondSlotAndFlatSentinel() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{5},
                new int[]{1},
                new int[0],
                new int[0],
                new int[0],
                null, null);

        ModelTriangle face = new ModelPacketBuilder(
                definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0).triangles().get(0);

        assertEquals(0, face.colorB());
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, face.colorC());
        assertTrue(face.flatShaded());
    }

    @Test
    void texturedRenderTypeThreeUsesSkipSentinelAndNeverSubmits() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 128, 0, 0, 0, 0, 128},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{5},
                new int[]{3},
                new int[0],
                new int[0],
                new int[0],
                null, null);

        ModelRenderPacket packet = new ModelPacketBuilder(
                definitions(ObjectAppearanceView.empty(), geometry))
                .build(document).get(0);
        ModelTriangle face = packet.triangles().get(0);

        assertEquals(ModelFaceColorContract.SKIP_SENTINEL, face.colorC());
        assertTrue(face.skippedByColorContract());
        assertTrue(packet.opaqueTriangleIndices().isEmpty());
        assertTrue(packet.transparentTriangleIndices().isEmpty());
    }

}
