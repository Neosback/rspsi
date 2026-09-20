package com.rspsi.editor.plugin.runtime;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level third-party plugin ecosystem coordinator.
 *
 * <p>Owns repository configuration, discovery, update planning and verified
 * package installation. UI code can stay thin and treat this service as the
 * single management boundary for a future Plugin Hub.</p>
 */
public final class PluginEcosystemService {
    private final Path pluginDirectory;
    private final PluginRepositoryConfigStore repositoryStore;
    private final ExternalPluginRuntime runtime;
    private final PluginRepositoryClient repositoryClient;
    private final PluginPackageInstaller installer;
    private final LinkedHashSet<URI> repositories = new LinkedHashSet<>();

    public PluginEcosystemService(Path pluginDirectory, Path repositoryConfig) {
        this(pluginDirectory, new PluginRepositoryConfigStore(repositoryConfig),
                new ExternalPluginRuntime(), new PluginRepositoryClient(),
                new PluginPackageInstaller());
    }

    PluginEcosystemService(Path pluginDirectory,
                           PluginRepositoryConfigStore repositoryStore,
                           ExternalPluginRuntime runtime,
                           PluginRepositoryClient repositoryClient,
                           PluginPackageInstaller installer) {
        this.pluginDirectory = Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        this.repositoryStore = Objects.requireNonNull(repositoryStore, "repositoryStore");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.repositoryClient = Objects.requireNonNull(repositoryClient, "repositoryClient");
        this.installer = Objects.requireNonNull(installer, "installer");
        repositories.addAll(repositoryStore.load());
    }

    public synchronized List<URI> repositories() {
        return List.copyOf(repositories);
    }

    public synchronized void addRepository(URI repository) {
        validateRepository(repository);
        if (repositories.add(repository)) persistRepositories();
    }

    public synchronized void removeRepository(URI repository) {
        if (repository != null && repositories.remove(repository)) persistRepositories();
    }

    public ExternalPluginRuntimeSnapshot scan(ClassLoader parent,
                                              Map<String, SemanticVersion> hostPlugins) {
        return runtime.discover(pluginDirectory, parent, hostPlugins);
    }

    public synchronized RepositoryRefresh refreshRepositories() {
        List<PluginRepositoryIndex> loaded = new ArrayList<>();
        Map<URI, String> failures = new LinkedHashMap<>();
        for (URI repository : repositories) {
            try {
                loaded.add(repositoryClient.fetch(repository));
            } catch (RuntimeException failure) {
                failures.put(repository, failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage());
            }
        }
        return new RepositoryRefresh(loaded, failures);
    }

    public List<PluginUpdatePlanner.Update> updates(
            ExternalPluginRuntimeSnapshot installed,
            SemanticVersion studioVersion) {
        Objects.requireNonNull(installed, "installed");
        RepositoryRefresh refresh = refreshRepositories();
        return PluginUpdatePlanner.plan(
                installed.manifests(), refresh.repositories(), studioVersion);
    }

    /** Installs one repository release atomically; the next scan activates it. */
    public Path install(PluginRepositoryEntry release) {
        return installer.install(release, pluginDirectory);
    }

    public List<Path> installUpdates(ExternalPluginRuntimeSnapshot installed,
                                     SemanticVersion studioVersion) {
        List<Path> installedPaths = new ArrayList<>();
        for (PluginUpdatePlanner.Update update : updates(installed, studioVersion)) {
            installedPaths.add(install(update.release()));
        }
        return List.copyOf(installedPaths);
    }

    public Path pluginDirectory() {
        return pluginDirectory;
    }

    private void persistRepositories() {
        repositoryStore.save(List.copyOf(repositories));
    }

    private static void validateRepository(URI repository) {
        Objects.requireNonNull(repository, "repository");
        String scheme = repository.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !"http".equalsIgnoreCase(scheme)
                && !"file".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Unsupported plugin repository URI: " + repository);
        }
    }

    public record RepositoryRefresh(
            List<PluginRepositoryIndex> repositories,
            Map<URI, String> failures) {
        public RepositoryRefresh {
            repositories = List.copyOf(repositories);
            failures = Map.copyOf(failures);
        }
    }
}
