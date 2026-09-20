package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPluginApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves managed plugin dependency/version constraints into deterministic load order. */
public final class PluginDependencyResolver {
    private PluginDependencyResolver() { }

    public static Resolution resolve(List<ExternalPluginCandidate> candidates,
                                     Map<String, SemanticVersion> hostPlugins) {
        Objects.requireNonNull(candidates, "candidates");
        Map<String, SemanticVersion> host = hostPlugins == null ? Map.of() : Map.copyOf(hostPlugins);
        Map<String, ExternalPluginCandidate> byId = new LinkedHashMap<>();
        Map<String, String> blocked = new LinkedHashMap<>();

        for (ExternalPluginCandidate candidate : candidates) {
            String id = candidate.manifest().id();
            if (byId.putIfAbsent(id, candidate) != null) {
                throw new IllegalArgumentException("Duplicate managed plugin id: " + id);
            }
            if (candidate.manifest().apiVersion() != EditorPluginApi.CURRENT_VERSION) {
                blocked.put(id, "Requires plugin API " + candidate.manifest().apiVersion()
                        + ", Studio provides " + EditorPluginApi.CURRENT_VERSION);
            }
        }

        boolean changed;
        do {
            changed = false;
            for (ExternalPluginCandidate candidate : candidates) {
                String id = candidate.manifest().id();
                if (blocked.containsKey(id)) continue;
                for (PluginDependency dependency : candidate.manifest().dependencies()) {
                    ExternalPluginCandidate managed = byId.get(dependency.id());
                    SemanticVersion available = managed != null
                            ? managed.manifest().version() : host.get(dependency.id());

                    if (available == null) {
                        if (!dependency.optional()) {
                            blocked.put(id, "Missing dependency " + dependency.id()
                                    + " " + dependency.version());
                            changed = true;
                            break;
                        }
                        continue;
                    }
                    if (!dependency.version().matches(available)) {
                        if (!dependency.optional()) {
                            blocked.put(id, "Dependency " + dependency.id() + " is " + available
                                    + " but requires " + dependency.version());
                            changed = true;
                            break;
                        }
                        continue;
                    }
                    if (managed != null && blocked.containsKey(dependency.id())
                            && !dependency.optional()) {
                        blocked.put(id, "Dependency " + dependency.id()
                                + " is unavailable: " + blocked.get(dependency.id()));
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (ExternalPluginCandidate candidate : candidates) {
            String id = candidate.manifest().id();
            if (blocked.containsKey(id)) continue;
            int count = 0;
            for (PluginDependency dependency : candidate.manifest().dependencies()) {
                ExternalPluginCandidate managed = byId.get(dependency.id());
                if (managed == null || blocked.containsKey(dependency.id())) continue;
                if (!dependency.version().matches(managed.manifest().version())) continue;
                count++;
                dependents.computeIfAbsent(dependency.id(), ignored -> new ArrayList<>()).add(id);
            }
            indegree.put(id, count);
        }

        List<String> ready = indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<ExternalPluginCandidate> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove(0);
            ordered.add(byId.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                    ready.sort(String::compareTo);
                }
            }
        }

        Set<String> orderedIds = new LinkedHashSet<>();
        ordered.forEach(candidate -> orderedIds.add(candidate.manifest().id()));
        for (String id : indegree.keySet()) {
            if (!orderedIds.contains(id)) {
                blocked.put(id, "Dependency cycle detected");
            }
        }

        return new Resolution(List.copyOf(ordered), Map.copyOf(blocked));
    }

    public record Resolution(List<ExternalPluginCandidate> ordered,
                             Map<String, String> blocked) {
        public Resolution {
            ordered = List.copyOf(ordered);
            blocked = Map.copyOf(blocked);
        }

        public boolean isLoadable(String pluginId) {
            return !blocked.containsKey(pluginId);
        }
    }
}
