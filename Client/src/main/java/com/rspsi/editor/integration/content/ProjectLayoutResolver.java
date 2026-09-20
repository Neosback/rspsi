package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pluggable discovery of declarative content roots for server project variants. */
public interface ProjectLayoutResolver {
    String id();
    int priority();
    Optional<ResolvedLayout> resolve(Path projectRoot);

    record ResolvedLayout(
            String resolverId,
            List<Path> contentRoots,
            List<Path> manifestRoots,
            Map<ContentCapability, List<Path>> knownRoots) {
        public ResolvedLayout {
            contentRoots = List.copyOf(contentRoots == null ? List.of() : contentRoots);
            manifestRoots = List.copyOf(manifestRoots == null ? List.of() : manifestRoots);
            knownRoots = knownRoots == null ? Map.of() : knownRoots.entrySet().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }
    }
}
