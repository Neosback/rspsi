package com.rspsi.osrs.rules.model;

/**
 * Formal OSRS sequence frame selection and looping rules.
 */
public final class AnimationResolver {
    private AnimationResolver() {}

    /**
     * Selects the active frame index using the OSRS sequence loop rule.
     * A positive frameStep rewinds that many frames after the initial pass.
     */
    public static int animationFrameIndex(
            int frameCount,
            int[] frameLengths,
            int frameStep,
            int clientCycle
    ) {
        if (frameCount <= 0) return 0;
        long[] durations = new long[frameCount];
        long initialDuration = 0;

        for (int index = 0; index < frameCount; index++) {
            durations[index] = Math.max(1L, (frameLengths != null && index < frameLengths.length)
                    ? frameLengths[index] : 1L) + 1L;
            initialDuration += durations[index];
        }

        long position = Math.max(0L, clientCycle);
        int loopStart = frameStep > 0 && frameStep <= frameCount
                ? frameCount - frameStep : frameCount;

        if (loopStart < frameCount && position >= initialDuration) {
            long loopDuration = 0;
            for (int index = loopStart; index < frameCount; index++) {
                loopDuration += durations[index];
            }
            if (loopDuration > 0) {
                position = sum(durations, 0, loopStart)
                        + (position - initialDuration) % loopDuration;
            }
        } else if (position >= initialDuration) {
            return frameCount - 1;
        }

        long elapsed = 0;
        for (int index = (loopStart < frameCount && position >= initialDuration) ? loopStart : 0;
             index < frameCount; index++) {
            if (position < elapsed + durations[index]) return index;
            elapsed += durations[index];
        }
        return frameCount - 1;
    }

    /**
     * Returns the first client cycle after {@code clientCycle} whose legacy
     * sequence frame index differs from the current one, or {@code -1} when
     * the sequence has reached a terminal frame that will never change again.
     *
     * <p>This is the scheduling counterpart of {@link #animationFrameIndex}.
     * It lets editor/runtime code sleep across a long frame instead of
     * rebuilding animation presentation on every 20 ms client cycle.</p>
     */
    public static int nextAnimationFrameChangeCycle(
            int frameCount,
            int[] frameLengths,
            int frameStep,
            int clientCycle
    ) {
        if (frameCount <= 0 || clientCycle < 0) return -1;

        long[] durations = new long[frameCount];
        long[] prefix = new long[frameCount + 1];
        for (int index = 0; index < frameCount; index++) {
            durations[index] = Math.max(1L, (frameLengths != null && index < frameLengths.length)
                    ? frameLengths[index] : 1L) + 1L;
            prefix[index + 1] = prefix[index] + durations[index];
        }

        long initialDuration = prefix[frameCount];
        int loopStart = frameStep > 0 && frameStep <= frameCount
                ? frameCount - frameStep : frameCount;
        long cycle = clientCycle;

        if (cycle < initialDuration) {
            int frame = frameAtPosition(prefix, cycle, 0);
            long boundary = prefix[frame + 1];
            if (frame < frameCount - 1) {
                return checkedFutureCycle(boundary);
            }
            if (loopStart >= frameCount || loopStart == frame) {
                return -1;
            }
            return checkedFutureCycle(boundary);
        }

        if (loopStart >= frameCount || loopStart == frameCount - 1) {
            return -1;
        }

        long loopDuration = initialDuration - prefix[loopStart];
        if (loopDuration <= 0L) return -1;
        long normalized = prefix[loopStart] + (cycle - initialDuration) % loopDuration;
        int frame = frameAtPosition(prefix, normalized, loopStart);
        long delta = prefix[frame + 1] - normalized;
        return checkedFutureCycle(cycle + delta);
    }

    /**
     * Selects the current frame for the client's cached-model skeletal path.
     * Cached sequences advance one frame per client cycle and use the same
     * frameCount loop-back field as the current AnimationSequence state machine.
     */
    public static int cachedFrameIndex(int frameCount, int frameStep, int clientCycle) {
        if (frameCount <= 0) return 0;
        long position = Math.max(0L, clientCycle);
        if (position < frameCount) return (int) position;

        if (frameStep > 0 && frameStep <= frameCount) {
            int loopStart = frameCount - frameStep;
            return loopStart + (int) ((position - frameCount) % frameStep);
        }

        // The client resets an invalid post-wrap frame to zero. Subsequent
        // cycles then advance normally until the next end crossing.
        return (int) (position % frameCount);
    }

    /**
     * Returns the first later client cycle whose cached-skeletal frame differs,
     * or {@code -1} when the selected frame is permanently stationary.
     */
    public static int nextCachedFrameChangeCycle(
            int frameCount,
            int frameStep,
            int clientCycle
    ) {
        if (frameCount <= 0 || clientCycle < 0 || clientCycle == Integer.MAX_VALUE) {
            return -1;
        }
        int current = cachedFrameIndex(frameCount, frameStep, clientCycle);
        // Cached sequences normally advance every cycle. A bounded scan also
        // handles the client's one-frame loop-back case without special
        // casing every post-wrap state.
        int limit = Math.max(2, frameCount + 1);
        for (int delta = 1; delta <= limit; delta++) {
            long candidate = (long) clientCycle + delta;
            if (candidate > Integer.MAX_VALUE) return -1;
            if (cachedFrameIndex(frameCount, frameStep, (int) candidate) != current) {
                return (int) candidate;
            }
        }
        return -1;
    }

    private static int frameAtPosition(long[] prefix, long position, int fromFrame) {
        for (int index = fromFrame; index < prefix.length - 1; index++) {
            if (position < prefix[index + 1]) return index;
        }
        return prefix.length - 2;
    }

    private static int checkedFutureCycle(long cycle) {
        return cycle > Integer.MAX_VALUE ? -1 : (int) cycle;
    }

    private static long sum(long[] values, int from, int count) {
        long total = 0;
        for (int index = from; index < from + count; index++) total += values[index];
        return total;
    }
}
