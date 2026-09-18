package com.rspsi.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small application-level preference store used before project settings exist. */
public final class StudioPreferences {
    private final Path root = Path.of(System.getProperty("user.home"), ".openrune-studio");
    private final Path recentCache = root.resolve("recent-cache.txt");

    public String recentCache() {
        try {
            if (!Files.isRegularFile(recentCache)) return "";
            return Files.readString(recentCache, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    public void rememberCache(Path cache) {
        if (cache == null) return;
        try {
            Files.createDirectories(root);
            Files.writeString(recentCache, cache.toAbsolutePath().normalize().toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // A failed preference write must not prevent an already-loaded cache
            // from being used for the current session.
        }
    }
}
