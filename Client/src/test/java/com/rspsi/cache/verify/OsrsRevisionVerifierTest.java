package com.rspsi.cache.verify;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsRevisionVerifierTest {
    @Test
    void verificationReportHasStableHumanReadableOutput() {
        OsrsRevisionVerifier.VerificationReport report = new OsrsRevisionVerifier.VerificationReport(
                Path.of("/tmp/example-cache"), 12850, 12850, 240, 12,
                true, true, false, List.of("cache opened"), List.of("semantic round-trip mismatch"));

        assertEquals("OSRS revision verification: /tmp/example-cache", report.lines().get(0));
        assertTrue(report.lines().contains("cache opened"));
        assertTrue(report.lines().contains("ERROR: semantic round-trip mismatch"));
    }
}
