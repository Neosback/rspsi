package com.rspsi.editor.plugin.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

/**
 * Child-first external plugin classloader with explicit dependency loaders and
 * parent-first Studio API packages.
 */
public final class IsolatedPluginClassLoader extends URLClassLoader {
    private static final List<String> PARENT_FIRST = List.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.rspsi.", "org.slf4j.");

    private final List<ClassLoader> dependencyLoaders;

    static {
        registerAsParallelCapable();
    }

    public IsolatedPluginClassLoader(URL pluginJar, ClassLoader parent,
                                     List<? extends ClassLoader> dependencyLoaders) {
        super(new URL[]{Objects.requireNonNull(pluginJar, "pluginJar")},
                Objects.requireNonNull(parent, "parent"));
        this.dependencyLoaders = List.copyOf(
                dependencyLoaders == null ? List.of() : dependencyLoaders);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;

            if (parentFirst(name)) {
                return super.loadClass(name, resolve);
            }

            try {
                Class<?> own = findClass(name);
                if (resolve) resolveClass(own);
                return own;
            } catch (ClassNotFoundException ignored) {
            }

            for (ClassLoader dependency : dependencyLoaders) {
                try {
                    return dependency.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static boolean parentFirst(String className) {
        for (String prefix : PARENT_FIRST) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }
}
