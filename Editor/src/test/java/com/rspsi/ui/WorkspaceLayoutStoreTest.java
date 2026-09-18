package com.rspsi.ui;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.ui.workspace.JsonWorkspaceLayoutStore;
import com.rspsi.ui.workspace.PanelLayoutState;
import com.rspsi.ui.workspace.WindowBounds;
import com.rspsi.ui.workspace.WorkspaceLayout;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLayoutStoreTest {
    @Test
    void layoutRoundTripsAndResetRemovesWorkspaceState() throws Exception {
        var directory = Files.createTempDirectory("rspsi-layouts");
        var store = new JsonWorkspaceLayoutStore(directory.resolve("layouts.json"));
        var layout = new WorkspaceLayout("map", WorkspaceLayout.CURRENT_VERSION, java.util.List.of(
                new PanelLayoutState("inspector", DockRegion.RIGHT, true, 0, 320,
                        true, new WindowBounds(40, 50, 480, 320))));

        store.save(layout);

        var reopened = new JsonWorkspaceLayoutStore(directory.resolve("layouts.json"));
        assertEquals(layout, reopened.load("map"));
        reopened.reset("map");
        assertNull(reopened.load("map"));
    }

    @Test
    void corruptUserLayoutFallsBackToDefaults() throws Exception {
        var directory = Files.createTempDirectory("rspsi-layouts-corrupt");
        var file = directory.resolve("layouts.json");
        Files.writeString(file, "not-json");

        var store = new JsonWorkspaceLayoutStore(file);

        assertNull(store.load("map"));
    }

    @Test
    void staleLayoutVersionsAreIgnored() throws Exception {
        var directory = Files.createTempDirectory("rspsi-layouts-version");
        var store = new JsonWorkspaceLayoutStore(directory.resolve("layouts.json"));
        store.save(new WorkspaceLayout("map", WorkspaceLayout.CURRENT_VERSION + 1,
                java.util.List.of()));

        var reopened = new JsonWorkspaceLayoutStore(directory.resolve("layouts.json"));
        assertNull(reopened.load("map"));
    }
}
