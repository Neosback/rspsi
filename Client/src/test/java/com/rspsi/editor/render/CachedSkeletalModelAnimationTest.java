package com.rspsi.editor.render;

import com.rspsi.cache.definition.AnimationCurveView;
import com.rspsi.cache.definition.CachedSkeletalAnimationView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ModelSkeletalSkinView;
import com.rspsi.cache.definition.SkeletalRigView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CachedSkeletalModelAnimationTest {
    @Test
    void appliesWeightedBoneTranslationLikeClientSkinningPath() {
        ModelGeometryView geometry = triangleGeometry()
                .withTriangleSkins(new int[]{2});
        ModelSkeletalSkinView skin = new ModelSkeletalSkinView(
                7,
                new int[][]{{0}, {0}, {0}},
                new int[][]{{255}, {255}, {255}});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(
                5, new int[]{0}, new int[][]{{0}},
                Optional.of(identityRig()));

        AnimationCurveView[][] boneCurves = new AnimationCurveView[1][9];
        boneCurves[0][3] = constant(10.0f);
        CachedSkeletalAnimationView animation =
                new CachedSkeletalAnimationView(
                        77, 5, 0, boneCurves, new AnimationCurveView[1]);

        ModelGeometryView result = CachedSkeletalModelAnimation.apply(
                geometry, skin, skeleton, animation, 0);

        assertNotEquals(geometry, result);
        assertEquals(10, result.vertexPositions()[0]);
        assertEquals(74, result.vertexPositions()[3]);
        assertEquals(10, result.vertexPositions()[6]);
    }

    @Test
    void cachedAlphaCurveUsesLegacyFaceSkinIndirection() {
        ModelGeometryView geometry = triangleGeometry()
                .withTriangleSkins(new int[]{2});
        ModelSkeletalSkinView skin = new ModelSkeletalSkinView(
                7,
                new int[][]{new int[0], new int[0], new int[0]},
                new int[][]{new int[0], new int[0], new int[0]});
        SkeletonDefinitionView skeleton = new SkeletonDefinitionView(
                5, new int[]{5}, new int[][]{{2}},
                Optional.of(identityRig()));

        AnimationCurveView[][] boneCurves = new AnimationCurveView[1][9];
        AnimationCurveView[] alpha = new AnimationCurveView[]{constant(0.1f)};
        CachedSkeletalAnimationView animation =
                new CachedSkeletalAnimationView(77, 5, 0, boneCurves, alpha);

        ModelGeometryView result = CachedSkeletalModelAnimation.apply(
                geometry, skin, skeleton, animation, 0);

        assertEquals(35, result.triangleAlphas()[0]);
    }

    private static ModelGeometryView triangleGeometry() {
        return new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{10},
                new int[]{-1})
                .withVertexSkins(new int[]{0, 0, 0});
    }

    private static SkeletalRigView identityRig() {
        return new SkeletalRigView(
                1,
                new int[]{-1},
                new float[][][]{{identity()}});
    }

    private static float[] identity() {
        float[] value = new float[16];
        value[0] = value[5] = value[10] = value[15] = 1.0f;
        return value;
    }

    private static AnimationCurveView constant(float value) {
        return new AnimationCurveView(
                AnimationCurveView.Extrapolation.CONSTANT,
                AnimationCurveView.Extrapolation.CONSTANT,
                false,
                new AnimationCurveView.Key[]{
                        new AnimationCurveView.Key(
                                0, value, 0.0f, 0.0f, 0.0f, 0.0f)
                });
    }
}
