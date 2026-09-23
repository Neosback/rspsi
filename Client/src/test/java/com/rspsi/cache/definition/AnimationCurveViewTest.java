package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationCurveViewTest {
    @Test
    void hermiteCurveMatchesEndpointTangents() {
        AnimationCurveView curve = new AnimationCurveView(
                AnimationCurveView.Extrapolation.CONSTANT,
                AnimationCurveView.Extrapolation.CONSTANT,
                false,
                new AnimationCurveView.Key[]{
                        new AnimationCurveView.Key(0, 0.0f, 0.0f, 0.0f, 10.0f, 10.0f),
                        new AnimationCurveView.Key(10, 10.0f, 10.0f, 10.0f, 0.0f, 0.0f)
                });

        assertEquals(0.0f, curve.valueAt(0), 0.0001f);
        assertEquals(5.0f, curve.valueAt(5), 0.0001f);
        assertEquals(10.0f, curve.valueAt(10), 0.0001f);
    }

    @Test
    void maxValueTangentProducesClientStepBehavior() {
        AnimationCurveView curve = new AnimationCurveView(
                AnimationCurveView.Extrapolation.CONSTANT,
                AnimationCurveView.Extrapolation.CONSTANT,
                false,
                new AnimationCurveView.Key[]{
                        new AnimationCurveView.Key(
                                0, 2.0f, 0.0f, 0.0f,
                                Float.MAX_VALUE, Float.MAX_VALUE),
                        new AnimationCurveView.Key(
                                5, 9.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                });

        assertEquals(2.0f, curve.valueAt(0), 0.0001f);
        assertEquals(9.0f, curve.valueAt(1), 0.0001f);
    }

    @Test
    void bezierCurveRetainsClientEndpoints() {
        AnimationCurveView curve = new AnimationCurveView(
                AnimationCurveView.Extrapolation.CONSTANT,
                AnimationCurveView.Extrapolation.CONSTANT,
                true,
                new AnimationCurveView.Key[]{
                        new AnimationCurveView.Key(0, 0.0f, 0.0f, 0.0f, 3.0f, 6.0f),
                        new AnimationCurveView.Key(9, 9.0f, 3.0f, 6.0f, 0.0f, 0.0f)
                });

        assertEquals(0.0f, curve.valueAt(0), 0.0001f);
        assertEquals(9.0f, curve.valueAt(9), 0.0001f);
    }

    @Test
    void linearExtrapolationUsesClientKeyTangents() {
        AnimationCurveView curve = new AnimationCurveView(
                AnimationCurveView.Extrapolation.LINEAR,
                AnimationCurveView.Extrapolation.LINEAR,
                false,
                new AnimationCurveView.Key[]{
                        new AnimationCurveView.Key(0, 4.0f, 2.0f, 4.0f, 2.0f, 4.0f),
                        new AnimationCurveView.Key(4, 12.0f, 2.0f, 4.0f, 2.0f, 4.0f)
                });

        assertEquals(0.0f, curve.valueAt(-2), 0.0001f);
        assertEquals(16.0f, curve.valueAt(6), 0.0001f);
    }
}
