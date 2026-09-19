package com.rspsi.cache.source;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Strategy interface for resolving and opening cache archives from different sources
 * (local directory, OpenRS2 download, or server project output).
 */
public interface CacheSourceProvider {

    String id();

    String name();

    boolean canOpen(Path path);

    LoadedOsrsCacheSession open(Path path);

    /** Built-in provider for standard on-disk OSRS caches containing main_file_cache.dat2. */
    final class LocalDiskCacheSourceProvider implements CacheSourceProvider {
        @Override
        public String id() {
            return "local.disk";
        }

        @Override
        public String name() {
            return "Local OSRS Cache";
        }

        @Override
        public boolean canOpen(Path path) {
            if (path == null || !Files.isDirectory(path)) return false;
            return Files.exists(path.resolve("main_file_cache.dat2"));
        }

        @Override
        public LoadedOsrsCacheSession open(Path path) {
            return LoadedOsrsCacheSession.open(path);
        }
    }
}
