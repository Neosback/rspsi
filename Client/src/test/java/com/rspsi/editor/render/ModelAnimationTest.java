package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelAnimationTest {
    @Test
    void appliesVertexGroupTranslationWithoutChangingTopology() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{0, 1}, new int[][]{{0}, {1}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0, 1}, new int[]{0, 5}, new int[]{0, -2}, new int[]{0, 0}, false);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{5, -2, 0, 15, -2, 0, 5, 8, 0}, animated.vertexPositions());
        assertArrayEquals(geometry.triangleIndices(), animated.triangleIndices());
    }

    @Test
    void appliesLegacyTypeFiveFaceAlphaAnimation() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{10}, new int[]{-1})
                .withTriangleSkins(new int[]{1});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{0, 5}, new int[][]{{}, {1}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{1}, new int[]{2}, new int[]{0}, new int[]{0}, true);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{26}, animated.triangleAlphas());
    }

    /**
     * Skeleton label ids are skin-label ids resolved through the model's
     * per-vertex skin map, not vertex indices. Here the label id (7) differs
     * from every vertex index; the client transforms all three members of
     * skin group 7.
     */
    @Test
    void resolvesSkeletonLabelsThroughVertexSkinMembership() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{7, 7, 7});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{1}, new int[][]{{7}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0}, new int[]{4}, new int[]{0}, new int[]{0}, false);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{4, 0, 0, 14, 0, 0, 4, 10, 0}, animated.vertexPositions());
    }

    /** Sparse skins leave non-member vertices untouched. */
    @Test
    void leavesVerticesOutsideTheTransformedSkinGroupUntouched() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 2});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{1}, new int[][]{{1}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0}, new int[]{4}, new int[]{0}, new int[]{0}, false);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{4, 0, 0, 14, 0, 0, 0, 10, 0}, animated.vertexPositions());
    }

    /**
     * Type 2 rolls about Z using the Z component first (melxin gate order:
     * roll(Z,tz), pitch(X,tx), yaw(Y,ty)), around the moving average pivot.
     * A 90-degree roll maps +X to -Y with the client's integer math.
     */
    @Test
    void appliesClientRotationOrderAndComponentMapping() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{3, 3, 3});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{2}, new int[][]{{3}});
        // tz = 64 -> angle 512 -> 90 degrees roll about Z through the origin.
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0}, new int[]{0}, new int[]{0}, new int[]{64}, false);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{0, 0, 0, 0, -10, 0, 10, 0, 0}, animated.vertexPositions());
    }

    /** The origin (type 0) transform averages its own skin group's members. */
    @Test
    void averagesOriginPivotFromTheTypesOwnSkinGroup() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 2});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{0, 2}, new int[][]{{1}, {2}});
        // Origin averages skin group 1: ((0,0,0)+(10,0,0))/2 + (0,-3,0).
        // Then the rotation group rotates vertex 2 about that moving pivot.
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0, 1}, new int[]{0, 0}, new int[]{-3, 0}, new int[]{0, 64}, false);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        // The origin transform only updates the pivot; it does not move any
        // vertex. Vertices 0 and 1 stay put, vertex 2 starts at (0,10,0);
        // relative to the pivot (5,-3,0) it is (-5,13,0); the client roll
        // maps (x,y) -> (y,-x), giving (13,5); adding the pivot -> (18,2,0).
        assertArrayEquals(new int[]{0, 0, 0, 10, 0, 0, 18, 2, 0}, animated.vertexPositions());
    }

    @Test
    void doesNotMutateTheSourceGeometry() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{1}, new int[][]{{1}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{0}, new int[]{4}, new int[]{0}, new int[]{0}, false);

        ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0}, geometry.vertexPositions());
        assertEquals(0, geometry.triangleAlphas()[0]);
    }
}
