package com.rspsi.editor.plugin;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ServiceLoader;

/** Discovers neutral editor plugins without knowing the active frontend. */
public final class EditorPluginLoader {
    private EditorPluginLoader() {
    }

    public static List<EditorPlugin> discover(ClassLoader classLoader) {
        return ServiceLoader.load(EditorPlugin.class, classLoader)
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(EditorPlugin::loadOrder)
                        .thenComparing(plugin -> String.valueOf(plugin.id())))
                .toList();
    }

    /**
     * Discovers external plugins with one isolated classloader per JAR artifact.
     * Faulty plugins are isolated so they do not crash discovery for valid plugins.
     */
    public static PluginDiscovery discoverOwned(Path directory, ClassLoader parent) {
        if (directory == null || parent == null || !Files.isDirectory(directory)) {
            return new PluginDiscovery(List.of(), List.of(), null);
        }
        List<Path> jarPaths = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jarPaths::add);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to inspect plugin directory " + directory, error);
        }
        if (jarPaths.isEmpty()) {
            return new PluginDiscovery(List.of(), List.of(), null);
        }

        List<PluginArtifact> artifacts = new ArrayList<>();
        List<EditorPlugin> discovered = new ArrayList<>();

        for (Path jarPath : jarPaths) {
            URL url;
            try {
                url = jarPath.toUri().toURL();
            } catch (IOException error) {
                continue;
            }
            URLClassLoader loader = URLClassLoader.newInstance(new URL[]{url}, parent);
            try {
                List<EditorPlugin> plugins = discover(loader);
                if (!plugins.isEmpty()) {
                    String hash = sha256(jarPath);
                    PluginArtifact artifact = new PluginArtifact(jarPath, hash, loader, plugins);
                    artifacts.add(artifact);
                    discovered.addAll(plugins);
                } else {
                    loader.close();
                }
            } catch (Throwable failure) {
                // Fault isolation: one failing plugin does not block others
                try {
                    loader.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        return new PluginDiscovery(discovered, artifacts, null);
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
