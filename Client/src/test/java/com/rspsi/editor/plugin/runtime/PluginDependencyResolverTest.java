package com.rspsi.editor.plugin.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PluginDependencyResolverTest {
    @Test
    void ordersManagedDependenciesAndBlocksMissingVersions() {
        ExternalPluginCandidate base = candidate("base.palette", "2.2.0", List.of());
        ExternalPluginCandidate brush = candidate("brush.pack", "1.0.0",
                List.of(PluginDependency.required("base.palette", "^2.0.0")));
        ExternalPluginCandidate broken = candidate("broken", "1.0.0",
                List.of(PluginDependency.required("missing", ">=1.0.0")));

        var result = PluginDependencyResolver.resolve(
                List.of(brush, broken, base), Map.of());

        assertEquals(List.of("base.palette", "brush.pack"),
                result.ordered().stream().map(value -> value.manifest().id()).toList());
        assertTrue(result.blocked().containsKey("broken"));
    }

    private static ExternalPluginCandidate candidate(String id, String version,
                                                     List<PluginDependency> dependencies) {
        ExternalPluginManifest manifest = new ExternalPluginManifest(
                1, id, id, SemanticVersion.parse(version), 1,
                "", List.of(), List.of(), "", "", "",
                SemanticVersion.parse("1.0.0"), dependencies, Set.of());
        return new ExternalPluginCandidate(Path.of(id + ".jar"), manifest, "0".repeat(64));
    }
}
