package com.rspsi.cache.definition;

import java.util.Arrays;

/**
 * Decoded cached-model animation curves. Bone channels use the client's
 * nine-channel rotation/translation/scale layout; alpha curves are keyed by
 * legacy skeleton transform index.
 */
public final class CachedSkeletalAnimationView {
    private final int id;
    private final int skeletonId;
    private final int poseIndex;
    private final AnimationCurveView[][] boneCurves;
    private final AnimationCurveView[] alphaCurves;

    public CachedSkeletalAnimationView(int id, int skeletonId, int poseIndex,
                                       AnimationCurveView[][] boneCurves,
                                       AnimationCurveView[] alphaCurves) {
        if (id < 0 || skeletonId < 0 || poseIndex < 0
                || boneCurves == null || alphaCurves == null) {
            throw new IllegalArgumentException("Invalid cached skeletal animation");
        }
        this.id = id;
        this.skeletonId = skeletonId;
        this.poseIndex = poseIndex;
        this.boneCurves = new AnimationCurveView[boneCurves.length][];
        for (int bone = 0; bone < boneCurves.length; bone++) {
            AnimationCurveView[] channels = boneCurves[bone];
            this.boneCurves[bone] = channels == null
                    ? new AnimationCurveView[9] : Arrays.copyOf(channels, 9);
        }
        this.alphaCurves = alphaCurves.clone();
    }

    public int id() { return id; }
    public int skeletonId() { return skeletonId; }
    public int poseIndex() { return poseIndex; }
    public int boneCount() { return boneCurves.length; }

    public AnimationCurveView boneCurve(int bone, int channel) {
        if (bone < 0 || bone >= boneCurves.length || channel < 0 || channel >= 9) return null;
        return boneCurves[bone][channel];
    }

    public AnimationCurveView alphaCurve(int transformIndex) {
        if (transformIndex < 0 || transformIndex >= alphaCurves.length) return null;
        return alphaCurves[transformIndex];
    }

    public boolean hasAlphaTransforms() {
        for (AnimationCurveView curve : alphaCurves) if (curve != null) return true;
        return false;
    }
}
