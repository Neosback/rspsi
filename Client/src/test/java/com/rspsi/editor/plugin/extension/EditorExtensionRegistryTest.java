package com.rspsi.editor.plugin.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorExtensionRegistryTest {
    private interface Solver {
        String solve();
    }

    @Test
    void ordersTypedExtensionsAndDisposesRegistrations() throws Exception {
        EditorExtensionRegistry registry = new EditorExtensionRegistry();
        ExtensionPoint<Solver> point = ExtensionPoint.of("terrain.wfc-solver", Solver.class);

        AutoCloseable low = registry.register(point, "baseline", 10, () -> "baseline");
        AutoCloseable high = registry.register(point, "community.fast", 100, () -> "fast");

        assertEquals("fast", registry.highestPriority(point).orElseThrow().solve());
        assertEquals(java.util.List.of("community.fast", "baseline"),
                registry.registrations(point).stream()
                        .map(EditorExtensionRegistry.Registration::id).toList());

        high.close();
        assertEquals("baseline", registry.highestPriority(point).orElseThrow().solve());
        low.close();
        assertTrue(registry.registrations(point).isEmpty());
    }
}
