package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectLoadStatusTest {

    @Test
    void normalizesNullableAndOutOfRangeInputs() {
        Throwable failure = new IllegalStateException("boom");

        ProjectLoadStatus low = new ProjectLoadStatus(null, -2.0, null, null, failure);
        assertEquals(ProjectLoadStatus.Phase.READ_DESCRIPTOR, low.phase());
        assertEquals(0.0, low.progress());
        assertEquals("", low.message());
        assertEquals("", low.detail());
        assertSame(failure, low.failure());
        assertFalse(low.failed());

        ProjectLoadStatus high = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.READY, 4.0, "done", "detail", null);
        assertEquals(1.0, high.progress());
        assertEquals("done", high.message());
        assertEquals("detail", high.detail());
    }

    @Test
    void initialStatusKeepsTheLauncherContract() {
        ProjectLoadStatus status = ProjectLoadStatus.initial();

        assertEquals(ProjectLoadStatus.Phase.READ_DESCRIPTOR, status.phase());
        assertEquals(0.05, status.progress());
        assertEquals("Reading project descriptor...", status.message());
        assertEquals("", status.detail());
        assertNull(status.failure());
        assertFalse(status.failed());
    }

    @Test
    void failedReflectsOnlyTheFailedPhase() {
        assertTrue(new ProjectLoadStatus(
                ProjectLoadStatus.Phase.FAILED, 0.5, "failed", "", null).failed());
        assertFalse(new ProjectLoadStatus(
                ProjectLoadStatus.Phase.READY, 1.0, "ready", "", null).failed());
    }

    @Test
    void valueSemanticsRemainAvailableAfterTheKotlinMigration() {
        ProjectLoadStatus left = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.VERIFY_CACHE, 0.68, "Checking", "cache", null);
        ProjectLoadStatus right = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.VERIFY_CACHE, 0.68, "Checking", "cache", null);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
