package com.rspsi.renderer.opengl;

import java.util.Arrays;

/**
 * Low-overhead frame telemetry for the native renderer.
 *
 * <p>CPU phases are measured every frame. GPU samples are supplied by the
 * asynchronous timer-query path only when a result is already available, so
 * collecting diagnostics never inserts a blocking query-result read into the
 * render loop.</p>
 */
final class RendererPerformanceMetrics {
    static final int WINDOW_SIZE = 120;

    private final long[] cpuFrameSamples = new long[WINDOW_SIZE];
    private final long[] gpuSceneSamples = new long[WINDOW_SIZE];
    private final long[] cpuSortedScratch = new long[WINDOW_SIZE];
    private final long[] gpuSortedScratch = new long[WINDOW_SIZE];
    private int cpuFrameSampleCount;
    private int gpuSceneSampleCount;
    private int cpuFrameCursor;
    private int gpuSceneCursor;

    private long frameStartedNanos;
    private long geometryUploadNanos;
    private long textureUploadNanos;
    private long visibilityNanos;
    private long orderingNanos;
    private long submissionNanos;

    private long lastCpuFrameNanos;
    private long lastGpuSceneNanos;
    private long lastPickerCpuNanos;
    private long lastPickerReadbackNanos;

    void beginFrame() {
        frameStartedNanos = System.nanoTime();
        geometryUploadNanos = 0L;
        textureUploadNanos = 0L;
        visibilityNanos = 0L;
        orderingNanos = 0L;
        submissionNanos = 0L;
    }

    void addGeometryUploadNanos(long nanos) {
        geometryUploadNanos += nonNegative(nanos);
    }

    void addTextureUploadNanos(long nanos) {
        textureUploadNanos += nonNegative(nanos);
    }

    void addVisibilityNanos(long nanos) {
        visibilityNanos += nonNegative(nanos);
    }

    void addOrderingNanos(long nanos) {
        orderingNanos += nonNegative(nanos);
    }

    void addSubmissionNanos(long nanos) {
        submissionNanos += nonNegative(nanos);
    }

    void endFrame() {
        if (frameStartedNanos == 0L) return;
        lastCpuFrameNanos = Math.max(0L, System.nanoTime() - frameStartedNanos);
        cpuFrameSamples[cpuFrameCursor] = lastCpuFrameNanos;
        cpuFrameCursor = (cpuFrameCursor + 1) % WINDOW_SIZE;
        cpuFrameSampleCount = Math.min(WINDOW_SIZE, cpuFrameSampleCount + 1);
        frameStartedNanos = 0L;
    }

    void recordGpuSceneNanos(long nanos) {
        if (nanos < 0L) return;
        lastGpuSceneNanos = nanos;
        gpuSceneSamples[gpuSceneCursor] = nanos;
        gpuSceneCursor = (gpuSceneCursor + 1) % WINDOW_SIZE;
        gpuSceneSampleCount = Math.min(WINDOW_SIZE, gpuSceneSampleCount + 1);
    }

    void recordPicker(long cpuNanos, long readbackNanos) {
        lastPickerCpuNanos = nonNegative(cpuNanos);
        lastPickerReadbackNanos = nonNegative(readbackNanos);
    }

    Snapshot snapshot() {
        sortSamples(cpuFrameSamples, cpuFrameSampleCount, cpuSortedScratch);
        sortSamples(gpuSceneSamples, gpuSceneSampleCount, gpuSortedScratch);
        return new Snapshot(
                lastCpuFrameNanos,
                geometryUploadNanos,
                textureUploadNanos,
                visibilityNanos,
                orderingNanos,
                submissionNanos,
                lastGpuSceneNanos,
                rankedPercentile(cpuSortedScratch, cpuFrameSampleCount, 0.50),
                rankedPercentile(cpuSortedScratch, cpuFrameSampleCount, 0.95),
                rankedPercentile(gpuSortedScratch, gpuSceneSampleCount, 0.50),
                rankedPercentile(gpuSortedScratch, gpuSceneSampleCount, 0.95),
                cpuFrameSampleCount,
                gpuSceneSampleCount,
                lastPickerCpuNanos,
                lastPickerReadbackNanos);
    }

    void reset() {
        Arrays.fill(cpuFrameSamples, 0L);
        Arrays.fill(gpuSceneSamples, 0L);
        Arrays.fill(cpuSortedScratch, 0L);
        Arrays.fill(gpuSortedScratch, 0L);
        cpuFrameSampleCount = 0;
        gpuSceneSampleCount = 0;
        cpuFrameCursor = 0;
        gpuSceneCursor = 0;
        frameStartedNanos = 0L;
        geometryUploadNanos = 0L;
        textureUploadNanos = 0L;
        visibilityNanos = 0L;
        orderingNanos = 0L;
        submissionNanos = 0L;
        lastCpuFrameNanos = 0L;
        lastGpuSceneNanos = 0L;
        lastPickerCpuNanos = 0L;
        lastPickerReadbackNanos = 0L;
    }

    static long percentile(long[] samples, int count, double percentile) {
        if (count <= 0) return 0L;
        if (count > samples.length) {
            throw new IllegalArgumentException("Sample count exceeds backing array");
        }
        if (!Double.isFinite(percentile) || percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be between 0 and 1");
        }
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        return rankedPercentile(copy, count, percentile);
    }

    private static void sortSamples(long[] samples, int count, long[] scratch) {
        if (count <= 0) return;
        System.arraycopy(samples, 0, scratch, 0, count);
        Arrays.sort(scratch, 0, count);
    }

    private static long rankedPercentile(long[] sortedSamples, int count, double percentile) {
        if (count <= 0) return 0L;
        int index = Math.max(0, Math.min(count - 1,
                (int) Math.ceil(percentile * count) - 1));
        return sortedSamples[index];
    }

    private static long nonNegative(long nanos) {
        return Math.max(0L, nanos);
    }

    record Snapshot(
            long cpuFrameNanos,
            long cpuGeometryUploadNanos,
            long cpuTextureUploadNanos,
            long cpuVisibilityNanos,
            long cpuOrderingNanos,
            long cpuSubmissionNanos,
            long gpuSceneNanos,
            long cpuFrameP50Nanos,
            long cpuFrameP95Nanos,
            long gpuSceneP50Nanos,
            long gpuSceneP95Nanos,
            int cpuFrameSampleCount,
            int gpuSceneSampleCount,
            long pickerCpuNanos,
            long pickerReadbackNanos
    ) {
    }
}
