package com.rspsi.renderer.opengl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RendererPerformanceMetricsTest {
    @Test
    void percentileUsesNearestRankWithoutMutatingInput() {
        long[] samples = {40L, 10L, 30L, 20L, 50L};

        assertEquals(30L, RendererPerformanceMetrics.percentile(samples, 5, 0.50));
        assertEquals(50L, RendererPerformanceMetrics.percentile(samples, 5, 0.95));
        assertEquals(40L, samples[0]);
    }

    @Test
    void percentileRejectsInvalidRequestsAndHandlesEmptyWindow() {
        assertEquals(0L, RendererPerformanceMetrics.percentile(new long[4], 0, 0.95));
        assertThrows(IllegalArgumentException.class,
                () -> RendererPerformanceMetrics.percentile(new long[4], 5, 0.95));
        assertThrows(IllegalArgumentException.class,
                () -> RendererPerformanceMetrics.percentile(new long[4], 1, 1.1));
    }

    @Test
    void pickerAndGpuSamplesArePublishedInSnapshot() {
        RendererPerformanceMetrics metrics = new RendererPerformanceMetrics();
        metrics.recordGpuSceneNanos(1234L);
        metrics.recordPicker(400L, 250L);

        RendererPerformanceMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1234L, snapshot.gpuSceneNanos());
        assertEquals(1234L, snapshot.gpuSceneP50Nanos());
        assertEquals(1, snapshot.gpuSceneSampleCount());
        assertEquals(400L, snapshot.pickerCpuNanos());
        assertEquals(250L, snapshot.pickerReadbackNanos());
    }
}
