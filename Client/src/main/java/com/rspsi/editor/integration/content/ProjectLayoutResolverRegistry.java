package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic resolver chain for stock and forked server layouts. */
public final class ProjectLayoutResolverRegistry {
    private final List<ProjectLayoutResolver> resolvers = new ArrayList<>();

    public synchronized AutoCloseable register(ProjectLayoutResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        if (resolvers.stream().anyMatch(value -> value.id().equals(resolver.id()))) {
            throw new IllegalArgumentException("Duplicate project layout resolver: " + resolver.id());
        }
        resolvers.add(resolver);
        resolvers.sort(Comparator.comparingInt(ProjectLayoutResolver::priority).reversed()
                .thenComparing(ProjectLayoutResolver::id));
        return () -> unregister(resolver.id());
    }

    public synchronized void unregister(String id) {
        resolvers.removeIf(value -> value.id().equals(id));
    }

    public synchronized Optional<ProjectLayoutResolver.ResolvedLayout> resolve(Path root) {
        for (ProjectLayoutResolver resolver : resolvers) {
            Optional<ProjectLayoutResolver.ResolvedLayout> resolved = resolver.resolve(root);
            if (resolved.isPresent()) return resolved;
        }
        return Optional.empty();
    }

    public synchronized List<ProjectLayoutResolver> resolvers() {
        return List.copyOf(resolvers);
    }
}
