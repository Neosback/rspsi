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

    private static long sum(long[] values, int from, int count) {
        long total = 0;
        for (int index = from; index < from + count; index++) total += values[index];
        return total;
    }
}
