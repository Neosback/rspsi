package com.rspsi.editor.plugin;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;
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
     * Discovers neutral plugins from an explicit jar directory. The caller
     * owns the directory policy; the neutral plugin API does not.
     */
    public static List<EditorPlugin> discover(Path directory, ClassLoader parent) {
        if (directory == null || parent == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<URL> urls = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            urls.add(path.toUri().toURL());
                        } catch (IOException error) {
                            throw new IllegalStateException("Unable to load plugin " + path, error);
                        }
                    });
        } catch (IOException error) {
            throw new IllegalStateException("Unable to inspect plugin directory " + directory, error);
        }
        if (urls.isEmpty()) {
            return List.of();
        }
        // Keep the loader reachable through the discovered plugin instances;
        // factories may load tool classes after initialization.
        URLClassLoader loader = URLClassLoader.newInstance(
                urls.toArray(URL[]::new), parent);
        return discover(loader);
    }
}
