package com.rspsi.editor.plugin.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Streaming integrity helpers shared by plugin discovery and installation. */
public final class PluginHashes {
    private PluginHashes() { }

    public static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash plugin artifact " + path, error);
        }
    }

    public static boolean matches(Path path, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) return false;
        return sha256(path).equalsIgnoreCase(expectedSha256.trim());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
