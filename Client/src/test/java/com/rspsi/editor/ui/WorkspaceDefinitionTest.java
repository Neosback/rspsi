package com.rspsi.editor.ui;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDefinitionTest {
    @Test
    void panelConstraintsAreImmutableAndValidatePreferredRegion() {
        PanelDescriptor inspector = new PanelDescriptor(
                "inspector", DockRegion.RIGHT,
                EnumSet.of(DockRegion.LEFT, DockRegion.RIGHT), 240, 320);

        assertTrue(inspector.allows(DockRegion.LEFT));
        assertFalse(inspector.allows(DockRegion.BOTTOM));
        assertThrows(UnsupportedOperationException.class,
                () -> inspector.allowedRegions().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new PanelDescriptor("bad", DockRegion.RIGHT,
                        EnumSet.of(DockRegion.LEFT), 1, 1));
    }

    @Test
    void workspaceRejectsDuplicatePanels() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceDefinition("terrain", List.of(
                new PanelPlacement("inspector", DockRegion.RIGHT, 0),
                new PanelPlacement("inspector", DockRegion.BOTTOM, 0))));
    }
}
