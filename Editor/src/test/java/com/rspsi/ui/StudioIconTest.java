package com.rspsi.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudioIconTest {
    @Test
    void everySemanticIconHasAStableGlyph() {
        for (StudioIcon icon : StudioIcon.values()) {
            assertNotNull(icon.glyph(), icon.name());
        }
    }
}
