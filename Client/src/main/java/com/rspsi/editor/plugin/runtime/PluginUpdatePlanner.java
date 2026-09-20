package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPluginApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects the highest compatible repository release for each installed managed plugin. */
public final class PluginUpdatePlanner {
    private PluginUpdatePlanner() { }

    public static List<Update> plan(List<ExternalPluginManifest> installed,
                                    List<PluginRepositoryIndex> repositories,
                                    SemanticVersion studioVersion) {
        Objects.requireNonNull(installed, "installed");
        Objects.requireNonNull(repositories, "repositories");
        Objects.requireNonNull(studioVersion, "studioVersion");

        Map<String, ExternalPluginManifest> current = new LinkedHashMap<>();
        installed.forEach(manifest -> current.put(manifest.id(), manifest));
        Map<String, PluginRepositoryEntry> best = new LinkedHashMap<>();

        for (PluginRepositoryIndex repository : repositories) {
            for (PluginRepositoryEntry entry : repository.plugins()) {
                ExternalPluginManifest manifest = entry.manifest();
                ExternalPluginManifest installedManifest = current.get(manifest.id());
                if (installedManifest == null) continue;
                if (manifest.apiVersion() != EditorPluginApi.CURRENT_VERSION) continue;
                if (manifest.minimumStudioVersion().compareTo(studioVersion) > 0) continue;
                if (manifest.version().compareTo(installedManifest.version()) <= 0) continue;
                best.merge(manifest.id(), entry, (first, second) ->
                        second.manifest().version().compareTo(first.manifest().version()) > 0
                                ? second : first);
            }
        }

        List<Update> updates = new ArrayList<>();
        best.forEach((id, entry) -> updates.add(new Update(
                id, current.get(id).version(), entry.manifest().version(), entry)));
        updates.sort(Comparator.comparing(Update::pluginId));
        return List.copyOf(updates);
    }

    public record Update(String pluginId, SemanticVersion installedVersion,
                         SemanticVersion availableVersion, PluginRepositoryEntry release) { }
}
