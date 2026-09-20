package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.PluginLoadFailure;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Managed third-party plugin runtime.
 *
 * <p>Managed JARs carry META-INF/rspsi-plugin.json, are dependency-resolved
 * before classloading, and receive one isolated classloader per artifact.
 * Manifest-less JARs continue to load as legacy isolated plugins so the
 * ecosystem can migrate without a flag day.</p>
 */
public final class ExternalPluginRuntime {
    private final PluginManifestCodec manifests = new PluginManifestCodec();

    public ExternalPluginRuntimeSnapshot discover(Path directory, ClassLoader parent,
                                                  Map<String, SemanticVersion> hostPlugins) {
        Objects.requireNonNull(parent, "parent");
        if (directory == null || !Files.isDirectory(directory)) {
            return new ExternalPluginRuntimeSnapshot(List.of(), List.of());
        }

        List<Path> jars;
        try (var stream = Files.list(directory)) {
            jars = stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted().toList();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to inspect plugin directory " + directory, error);
        }

        List<ExternalPluginCandidate> managed = new ArrayList<>();
        List<Path> legacy = new ArrayList<>();
        List<PluginLoadFailure> failures = new ArrayList<>();
        for (Path jar : jars) {
            try {
                var manifest = manifests.readJarManifest(jar);
                if (manifest.isPresent()) {
                    managed.add(new ExternalPluginCandidate(
                            jar, manifest.orElseThrow(), PluginHashes.sha256(jar)));
                } else {
                    legacy.add(jar);
                }
            } catch (Throwable error) {
                failures.add(failure(jar, "Invalid plugin manifest: " + message(error), error));
            }
        }

        // Versioned installs intentionally keep old artifacts available for rollback.
        // Select the highest installed release of each managed plugin before dependency
        // resolution so side-by-side versions never become a duplicate-id failure.
        managed = selectInstalledReleases(managed, failures);

        PluginDependencyResolver.Resolution resolution;
        try {
            resolution = PluginDependencyResolver.resolve(managed, hostPlugins);
        } catch (RuntimeException error) {
            for (ExternalPluginCandidate candidate : managed) {
                failures.add(failure(candidate.jarPath(), message(error), error));
            }
            resolution = new PluginDependencyResolver.Resolution(List.of(), Map.of());
        }

        Map<String, ExternalPluginCandidate> managedById = new LinkedHashMap<>();
        managed.forEach(candidate -> managedById.put(candidate.manifest().id(), candidate));
        resolution.blocked().forEach((id, reason) -> {
            ExternalPluginCandidate candidate = managedById.get(id);
            if (candidate != null) {
                failures.add(failure(candidate.jarPath(), reason, new IllegalStateException(reason)));
            }
        });

        List<ExternalPluginHandle> handles = new ArrayList<>();
        Map<String, ExternalPluginHandle> handlesById = new LinkedHashMap<>();
        java.util.Set<String> loadedPluginIds = new java.util.LinkedHashSet<>();

        for (ExternalPluginCandidate candidate : resolution.ordered()) {
            ExternalPluginManifest manifest = candidate.manifest();
            List<ClassLoader> dependencyLoaders = new ArrayList<>();
            String missingLoadedDependency = null;
            for (PluginDependency dependency : manifest.dependencies()) {
                ExternalPluginHandle dependencyHandle = handlesById.get(dependency.id());
                if (dependencyHandle != null) {
                    dependencyLoaders.add(dependencyHandle.classLoader());
                } else if (managedById.containsKey(dependency.id()) && !dependency.optional()) {
                    missingLoadedDependency = dependency.id();
                    break;
                }
            }
            if (missingLoadedDependency != null) {
                String reason = "Dependency " + missingLoadedDependency + " failed to load";
                failures.add(failure(candidate.jarPath(), reason, new IllegalStateException(reason)));
                continue;
            }

            IsolatedPluginClassLoader loader = null;
            try {
                loader = new IsolatedPluginClassLoader(
                        candidate.jarPath().toUri().toURL(), parent, dependencyLoaders);
                List<EditorPlugin> local = discoverLocal(loader);
                List<EditorPlugin> matching = local.stream()
                        .filter(plugin -> manifest.id().equals(plugin.id())).toList();
                if (matching.size() != 1 || local.size() != 1) {
                    throw new IllegalStateException("Managed plugin JAR " + candidate.jarPath()
                            + " must expose exactly one EditorPlugin matching manifest id "
                            + manifest.id() + "; discovered "
                            + local.stream().map(EditorPlugin::id).toList());
                }
                if (!loadedPluginIds.add(manifest.id())) {
                    throw new IllegalStateException("Duplicate external plugin id: " + manifest.id());
                }
                EditorPlugin wrapped = new ManifestBackedEditorPlugin(manifest, matching.get(0));
                ExternalPluginHandle handle = new ExternalPluginHandle(
                        candidate.jarPath(), candidate.sha256(), manifest, loader, List.of(wrapped));
                handles.add(handle);
                handlesById.put(manifest.id(), handle);
                loader = null;
            } catch (Throwable error) {
                failures.add(failure(candidate.jarPath(), message(error), error));
                closeQuietly(loader, error);
            }
        }

        // Backwards compatibility: manifest-less plugins stay isolated but do
        // not participate in dependency resolution or update feeds.
        for (Path jar : legacy) {
            IsolatedPluginClassLoader loader = null;
            try {
                loader = new IsolatedPluginClassLoader(jar.toUri().toURL(), parent, List.of());
                List<EditorPlugin> local = discoverLocal(loader);
                if (local.isEmpty()) {
                    loader.close();
                    loader = null;
                    continue;
                }
                for (EditorPlugin plugin : local) {
                    if (!loadedPluginIds.add(plugin.id())) {
                        throw new IllegalStateException("Duplicate external plugin id: " + plugin.id());
                    }
                }
                ExternalPluginHandle handle = new ExternalPluginHandle(
                        jar, PluginHashes.sha256(jar), null, loader, local);
                handles.add(handle);
                loader = null;
            } catch (Throwable error) {
                failures.add(failure(jar, message(error), error));
                closeQuietly(loader, error);
            }
        }

        return new ExternalPluginRuntimeSnapshot(handles, failures);
    }

    private static List<ExternalPluginCandidate> selectInstalledReleases(
            List<ExternalPluginCandidate> candidates,
            List<PluginLoadFailure> failures) {
        Map<String, List<ExternalPluginCandidate>> byId = new LinkedHashMap<>();
        for (ExternalPluginCandidate candidate : candidates) {
            byId.computeIfAbsent(candidate.manifest().id(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        List<ExternalPluginCandidate> selected = new ArrayList<>();
        for (Map.Entry<String, List<ExternalPluginCandidate>> entry : byId.entrySet()) {
            List<ExternalPluginCandidate> releases = new ArrayList<>(entry.getValue());
            releases.sort(Comparator
                    .comparing((ExternalPluginCandidate value) -> value.manifest().version())
                    .reversed()
                    .thenComparing(value -> value.jarPath().toString()));
            ExternalPluginCandidate newest = releases.get(0);
            selected.add(newest);

            for (int index = 1; index < releases.size(); index++) {
                ExternalPluginCandidate older = releases.get(index);
                if (older.manifest().version().equals(newest.manifest().version())
                        && !older.sha256().equalsIgnoreCase(newest.sha256())) {
                    String reason = "Conflicting artifacts provide " + entry.getKey()
                            + " " + newest.manifest().version()
                            + "; using " + newest.jarPath().getFileName();
                    failures.add(failure(older.jarPath(), reason,
                            new IllegalStateException(reason)));
                }
            }
        }
        selected.sort(Comparator.comparing(candidate -> candidate.manifest().id()));
        return List.copyOf(selected);
    }

    private static List<EditorPlugin> discoverLocal(ClassLoader loader) {
        return ServiceLoader.load(EditorPlugin.class, loader).stream()
                .filter(provider -> provider.type().getClassLoader() == loader)
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(EditorPlugin::loadOrder)
                        .thenComparing(EditorPlugin::id))
                .toList();
    }

    private static PluginLoadFailure failure(Path jar, String message, Throwable cause) {
        Throwable nonNull = cause == null ? new IllegalStateException(message) : cause;
        return new PluginLoadFailure(jar,
                message == null || message.isBlank() ? nonNull.toString() : message, nonNull);
    }

    private static String message(Throwable error) {
        if (error == null) return "Plugin load failed";
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static void closeQuietly(IsolatedPluginClassLoader loader, Throwable failure) {
        if (loader == null) return;
        try {
            loader.close();
        } catch (IOException closeError) {
            if (failure != null) failure.addSuppressed(closeError);
        }
    }
}
