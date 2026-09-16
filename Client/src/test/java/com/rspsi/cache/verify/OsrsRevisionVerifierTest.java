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

    @Test
    void checksAreRenderedAsAnAuditableGate() {
        OsrsRevisionVerifier.VerificationReport report = new OsrsRevisionVerifier.VerificationReport(
                Path.of("/tmp/example-cache"), null, null, null, 12,
                true, false, false, List.of(), List.of(), List.of(
                        new VerificationCheck("cache.open", VerificationCheck.Status.PASS, "opened"),
                        new VerificationCheck("render.parity", VerificationCheck.Status.NOT_RUN, "fixture pending")));

        assertTrue(report.lines().contains("PASS cache.open: opened"));
        assertTrue(report.lines().contains("NOT_RUN render.parity: fixture pending"));
    }
}
