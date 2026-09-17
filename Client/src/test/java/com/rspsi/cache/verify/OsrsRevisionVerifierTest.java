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

    @Test
    void strictParityModeTurnsMissingIndependentEvidenceIntoErrors() {
        VerificationCheck render = new VerificationCheck("render.parity",
                VerificationCheck.Status.NOT_RUN, "fixture pending");
        VerificationCheck minimap = new VerificationCheck("minimap.parity",
                VerificationCheck.Status.WARN, "no images");

        List<String> errors = OsrsRevisionVerifier.requiredParityErrors(render, minimap, true);

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("render parity"));
        assertTrue(errors.get(1).contains("minimap parity"));
        assertTrue(OsrsRevisionVerifier.requiredParityErrors(render, minimap, false).isEmpty());
    }

    @Test
    void strictParityModeAcceptsOnlyPassingChecks() {
        VerificationCheck render = new VerificationCheck("render.parity",
                VerificationCheck.Status.PASS, "matched");
        VerificationCheck minimap = new VerificationCheck("minimap.parity",
                VerificationCheck.Status.PASS, "matched");

        assertTrue(OsrsRevisionVerifier.requiredParityErrors(render, minimap, true).isEmpty());
    }
}
