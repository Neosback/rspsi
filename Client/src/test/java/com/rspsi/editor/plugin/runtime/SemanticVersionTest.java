package com.rspsi.editor.plugin.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticVersionTest {
    @Test
    void parsesAndOrdersReleaseAndPrereleaseVersions() {
        assertTrue(SemanticVersion.parse("1.2.0")
                .compareTo(SemanticVersion.parse("1.2.0-beta.2")) > 0);
        assertTrue(SemanticVersion.parse("2")
                .compareTo(SemanticVersion.parse("1.99.99")) > 0);
        assertEquals("1.2.3-alpha", SemanticVersion.parse("v1.2.3-alpha+build").toString());
    }

    @Test
    void supportsCaretTildeAndComparisonRanges() {
        assertTrue(VersionConstraint.parse("^1.2.3").matches(SemanticVersion.parse("1.9.0")));
        assertFalse(VersionConstraint.parse("^1.2.3").matches(SemanticVersion.parse("2.0.0")));
        assertTrue(VersionConstraint.parse(">=1.2 <2.0").matches(SemanticVersion.parse("1.5")));
        assertFalse(VersionConstraint.parse("~1.2.3").matches(SemanticVersion.parse("1.3.0")));
    }
}
