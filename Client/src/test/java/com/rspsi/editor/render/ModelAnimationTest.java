package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ModelAnimationTest {
    @Test
    void appliesVertexGroupTranslationWithoutChangingTopology() {
        ModelGeometryView geometry = new ModelGeometryView(1,
                new int[]{0, 0, 0, 10, 0, 0, 0, 10, 0},
                new int[]{0, 1, 2}, new short[]{1}, new int[]{0}, new int[]{-1})
                .withVertexSkins(new int[]{1, 1, 1});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(2,
                new int[]{0, 1}, new int[][]{{0}, {0, 1, 2}});
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
                new int[]{0, 5}, new int[][]{{}, {0}});
        AnimationFrameView frame = new AnimationFrameView(3, 2,
                new int[]{1}, new int[]{2}, new int[]{0}, new int[]{0}, true);

        ModelGeometryView animated = ModelAnimation.apply(geometry, frame, skeleton);

        assertArrayEquals(new int[]{26}, animated.triangleAlphas());
    }
}
