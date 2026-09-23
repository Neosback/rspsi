package com.rspsi.editor.render;

/**
 * Frame-time animation state retained with one rendered model packet.
 *
 * <p>The sequence/frame identity is presentation state. It must not replace the
 * stable {@link SceneObjectIdentity} of the placed scene object. The client
 * animation-height offset is applied as a render-space Y translation while the
 * authored terrain placement height remains unchanged.</p>
 */
public record ModelAnimationState(
        int sequenceId,
        int frameIndex,
        int frameId,
        int clientCycle,
        int animationHeightOffset,
        boolean transformed
) {
    public ModelAnimationState {
        if (sequenceId < -1 || frameIndex < -1 || frameId < -1 || clientCycle < 0
                || animationHeightOffset < Byte.MIN_VALUE
                || animationHeightOffset > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid model animation state");
        }
        if (sequenceId < 0 && (frameIndex >= 0 || frameId >= 0 || transformed)) {
            throw new IllegalArgumentException("Inactive animation cannot carry frame state");
        }
        if ((frameIndex >= 0) != (frameId >= 0)) {
            throw new IllegalArgumentException("Animation frame index/id must be present together");
        }
        if (transformed && (sequenceId < 0 || frameIndex < 0)) {
            throw new IllegalArgumentException("Transformed animation requires an active frame");
        }
    }

    public static ModelAnimationState none() {
        return new ModelAnimationState(-1, -1, -1, 0, 0, false);
    }

    public static ModelAnimationState unresolved(int sequenceId, int clientCycle) {
        return unresolved(sequenceId, clientCycle, 0);
    }

    public static ModelAnimationState unresolved(int sequenceId, int clientCycle,
                                                 int animationHeightOffset) {
        if (sequenceId < 0) return none();
        return new ModelAnimationState(sequenceId, -1, -1, clientCycle,
                animationHeightOffset, false);
    }

    public static ModelAnimationState selected(int sequenceId, int frameIndex, int frameId,
                                               int clientCycle, int animationHeightOffset,
                                               boolean transformed) {
        return new ModelAnimationState(sequenceId, frameIndex, frameId, clientCycle,
                animationHeightOffset, transformed);
    }

    public boolean active() {
        return sequenceId >= 0;
    }

    public boolean frameSelected() {
        return frameIndex >= 0;
    }

    public ModelAnimationState withTransformed(boolean value) {
        if (!frameSelected() && value) {
            throw new IllegalArgumentException("Animation without a selected frame cannot be transformed");
        }
        return new ModelAnimationState(sequenceId, frameIndex, frameId, clientCycle,
                animationHeightOffset, value);
    }

    /** Client draw-space Y after Renderable#getAnimationHeightOffset is applied. */
    public int renderPlacementHeight(int placementHeight) {
        return active() ? placementHeight - animationHeightOffset : placementHeight;
    }
}
