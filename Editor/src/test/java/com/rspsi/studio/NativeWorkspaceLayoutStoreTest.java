package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeWorkspaceLayoutStoreTest {
    @Test
    void nativeIniAndDrawerStateRoundTrip() throws Exception {
        var directory = Files.createTempDirectory("openrune-native-layout");
        var store = new NativeWorkspaceLayoutStore(directory.resolve("layout.json"));
        var state = new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION,
                "[Docking][Data]\nDockNode ID=0x123", false);

        store.save(state);

        var reopened = new NativeWorkspaceLayoutStore(directory.resolve("layout.json"));
        assertEquals(state, reopened.load());
        reopened.reset();
        assertNull(reopened.load());
    }
}
