package com.rspsi.editor.corpus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Compact flat-file persistence for precomputed region feature vectors. */
public final class RegionFingerprintStore {
    private final ObjectMapper mapper = new ObjectMapper();

    public void save(Path path, List<RegionFingerprint> fingerprints) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fingerprints, "fingerprints");
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), fingerprints);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save region fingerprint index " + path, error);
        }
    }

    public List<RegionFingerprint> load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) return List.of();
        try {
            return List.copyOf(mapper.readValue(path.toFile(),
                    new TypeReference<List<RegionFingerprint>>() { }));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load region fingerprint index " + path, error);
        }
    }
}
