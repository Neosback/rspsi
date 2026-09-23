package com.rspsi.cache.definition;

import java.util.Arrays;
import java.util.Objects;

/** Stable, editor-facing animation sequence metadata. */
public record SequenceDefinitionView(
        int id,
        int[] frameIds,
        int[] frameLengths,
        int frameStep,
        boolean stretches,
        int leftHandItem,
        int rightHandItem,
        int maxLoops,
        int precedenceAnimating,
        int priority,
        int replyMode,
        int skeletalId,
        int animationHeightOffset) {
    public SequenceDefinitionView {
        if (id < 0 || frameIds == null || frameLengths == null
                || frameIds.length != frameLengths.length
                || frameStep < -1 || leftHandItem < -1 || rightHandItem < -1
                || maxLoops < 0 || precedenceAnimating < -1 || priority < -1
                || replyMode < 0 || skeletalId < -1
                || animationHeightOffset < Byte.MIN_VALUE
                || animationHeightOffset > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid sequence definition");
        }
        frameIds = frameIds.clone();
        frameLengths = frameLengths.clone();
    }

    /** Compatibility constructor before the client animation-height offset was exposed. */
    public SequenceDefinitionView(
            int id,
            int[] frameIds,
            int[] frameLengths,
            int frameStep,
            boolean stretches,
            int leftHandItem,
            int rightHandItem,
            int maxLoops,
            int precedenceAnimating,
            int priority,
            int replyMode,
            int skeletalId) {
        this(id, frameIds, frameLengths, frameStep, stretches, leftHandItem, rightHandItem,
                maxLoops, precedenceAnimating, priority, replyMode, skeletalId, 0);
    }

    @Override
    public int[] frameIds() {
        return frameIds.clone();
    }

    @Override
    public int[] frameLengths() {
        return frameLengths.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SequenceDefinitionView value)) return false;
        return id == value.id && frameStep == value.frameStep
                && stretches == value.stretches && leftHandItem == value.leftHandItem
                && rightHandItem == value.rightHandItem && maxLoops == value.maxLoops
                && precedenceAnimating == value.precedenceAnimating
                && priority == value.priority && replyMode == value.replyMode
                && skeletalId == value.skeletalId
                && animationHeightOffset == value.animationHeightOffset
                && Arrays.equals(frameIds, value.frameIds)
                && Arrays.equals(frameLengths, value.frameLengths);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, frameStep, stretches, leftHandItem, rightHandItem,
                maxLoops, precedenceAnimating, priority, replyMode, skeletalId,
                animationHeightOffset);
        result = 31 * result + Arrays.hashCode(frameIds);
        return 31 * result + Arrays.hashCode(frameLengths);
    }
}
