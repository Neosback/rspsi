package com.rspsi.cache.store;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a new writable output cache from a read-only source cache and a set
 * of validated object-definition transactions.
 *
 * <p>The source directory is never opened writable. A new sibling staging
 * directory is cloned from the source, definition payloads are written there,
 * the staged cache is closed and reopened read-only for byte and semantic
 * verification, and only then is the staging directory moved into the caller's
 * requested output path.</p>
 */
public final class ObjectDefinitionOutputCacheBuilder {

    private ObjectDefinitionOutputCacheBuilder() {
    }

    /**
     * Creates a new output cache containing the source cache plus all dirty
     * object-definition transactions.
     *
     * @throws IllegalArgumentException if the output path aliases/nests under
     *                                  the source, already exists, transactions
     *                                  are clean/duplicate, or a transaction
     *                                  does not belong to the selected source
     * @throws IOException              if staging/copy/move operations fail
     */
    public static BuildResult buildNewOutput(
            Path sourceCache,
            Path outputCache,
            int revision,
            Collection<? extends ObjectDefinitionEditTransaction> transactions)
            throws IOException {

        Objects.requireNonNull(sourceCache, "sourceCache");
        Objects.requireNonNull(outputCache, "outputCache");
        Objects.requireNonNull(transactions, "transactions");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }

        Path source = sourceCache.toAbsolutePath().normalize();
        Path output = outputCache.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source cache directory does not exist: " + source);
        }
        Path sourceReal = source.toRealPath();

        validateOutputPath(source, sourceReal, output);

        List<EncodedEdit> edits = prepareEdits(sourceReal, revision, transactions);
        Path outputParent = output.getParent();
        if (outputParent == null) {
            throw new IllegalArgumentException("Output cache must have a parent directory");
        }
        Files.createDirectories(outputParent);

        Path staging = Files.createTempDirectory(
                outputParent,
                "." + output.getFileName() + "-staging-");
        try {
            copyCacheDirectory(sourceReal, staging);
            writeStagedDefinitions(staging, edits);

            // Reopen and verify the complete staged cache before the requested
            // output path exists. The move below is the publication boundary.
            verifyCache(staging, revision, edits);
            moveIntoPlace(staging, output);

            long bytes = edits.stream().mapToLong(edit -> edit.payload().length).sum();
            return new BuildResult(
                    output,
                    edits.stream().map(EncodedEdit::objectId).toList(),
                    bytes);
        } catch (IOException | RuntimeException failure) {
            cleanupTree(staging, failure);
            throw failure;
        }
    }

    private static List<EncodedEdit> prepareEdits(
            Path source,
            int revision,
            Collection<? extends ObjectDefinitionEditTransaction> transactions) {

        Map<Integer, ObjectDefinitionEditTransaction> dirty = new LinkedHashMap<>();
        for (ObjectDefinitionEditTransaction transaction : transactions) {
            ObjectDefinitionEditTransaction checked =
                    Objects.requireNonNull(transaction, "transaction");
            if (!checked.dirty()) {
                continue;
            }
            ObjectDefinitionEditTransaction previous = dirty.putIfAbsent(
                    checked.id(), checked);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate object definition transaction for id " + checked.id());
            }
        }
        if (dirty.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one dirty object definition transaction is required");
        }

        try (OpenRuneCacheStore sourceStore = OpenRuneCacheStore.open(source)) {
            List<EncodedEdit> edits = new ArrayList<>(dirty.size());
            dirty.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        int objectId = entry.getKey();
                        ObjectDefinitionEditTransaction transaction = entry.getValue();

                        byte[] sourcePayload =
                                sourceStore.readObjectDefinitionPayload(objectId);
                        if (sourcePayload == null) {
                            throw new IllegalArgumentException(
                                    "Source cache does not contain object definition " + objectId);
                        }

                        ObjectDefinitionRawView sourceRaw =
                                sourceStore.decodeObjectDefinitionPayload(
                                        objectId, sourcePayload, revision);
                        if (!sourceRaw.equals(transaction.original())) {
                            throw new IllegalArgumentException(
                                    "Object definition transaction " + objectId
                                            + " does not match the selected source cache");
                        }

                        byte[] encoded = transaction.encodeValidated();
                        ObjectDefinitionRawView expected = transaction.preview();
                        ObjectDefinitionRawView encodedRaw =
                                sourceStore.decodeObjectDefinitionPayload(
                                        objectId, encoded, revision);
                        if (!expected.equals(encodedRaw)) {
                            throw new IllegalStateException(
                                    "Validated payload does not decode to the transaction preview "
                                            + "for object " + objectId);
                        }
                        edits.add(new EncodedEdit(objectId, expected, encoded));
                    });
            return List.copyOf(edits);
        }
    }

    private static void writeStagedDefinitions(Path staging, List<EncodedEdit> edits) {
        try (OpenRuneCacheStore output = OpenRuneCacheStore.openWritable(staging)) {
            for (EncodedEdit edit : edits) {
                output.writeObjectDefinitionPayload(edit.objectId(), edit.payload());
            }
            output.flush();
        }
    }

    private static void verifyCache(
            Path cachePath,
            int revision,
            List<EncodedEdit> edits) {

        try (OpenRuneCacheStore reopened = OpenRuneCacheStore.open(cachePath)) {
            for (EncodedEdit edit : edits) {
                byte[] actual = reopened.readObjectDefinitionPayload(edit.objectId());
                if (actual == null) {
                    throw new IllegalStateException(
                            "Written object definition is missing after reopen: "
                                    + edit.objectId());
                }
                if (!Arrays.equals(edit.payload(), actual)) {
                    throw new IllegalStateException(
                            "Written object definition bytes changed after reopen: "
                                    + edit.objectId());
                }

                ObjectDefinitionRawView decoded =
                        reopened.decodeObjectDefinitionPayload(
                                edit.objectId(), actual, revision);
                if (!edit.preview().equals(decoded)) {
                    throw new IllegalStateException(
                            "Written object definition semantic mismatch after reopen: "
                                    + edit.objectId());
                }

                byte[] canonical =
                        reopened.canonicalObjectDefinitionPayload(
                                edit.objectId(), actual, revision);
                if (!Arrays.equals(actual, canonical)) {
                    throw new IllegalStateException(
                            "Written object definition is not canonical after reopen: "
                                    + edit.objectId());
                }
            }
        }
    }

    private static void validateOutputPath(
            Path sourceAbsolute,
            Path sourceReal,
            Path output) throws IOException {

        if (sourceAbsolute.equals(output)) {
            throw new IllegalArgumentException(
                    "Source and output cache paths must differ");
        }
        if (Files.exists(output)) {
            if (Files.isSameFile(sourceReal, output)) {
                throw new IllegalArgumentException(
                        "Source and output cache paths resolve to the same directory");
            }
            throw new IllegalArgumentException(
                    "Output cache already exists; buildNewOutput never overwrites: " + output);
        }

        // Reject the obvious lexical nesting before creating any directories.
        if (output.startsWith(sourceAbsolute)) {
            throw new IllegalArgumentException(
                    "Output cache cannot be created inside the source cache");
        }

        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output cache must have a parent directory");
        }

        Path existingAncestor = parent;
        while (existingAncestor != null && !Files.exists(existingAncestor)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IllegalArgumentException(
                    "Output cache has no resolvable parent directory");
        }

        Path ancestorReal = existingAncestor.toRealPath();
        Path candidate = ancestorReal.resolve(
                existingAncestor.relativize(output)).normalize();
        if (candidate.startsWith(sourceReal)) {
            throw new IllegalArgumentException(
                    "Output cache cannot resolve inside the source cache");
        }
    }

    private static void copyCacheDirectory(Path source, Path target)
            throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException(
                            "Cache clone does not follow symbolic-link directories: "
                                    + directory);
                }
                Path relative = source.relativize(directory);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file) || !attributes.isRegularFile()) {
                    throw new IOException(
                            "Cache clone only supports regular files: " + file);
                }
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveIntoPlace(Path staging, Path output)
            throws IOException {
        try {
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, output);
        }
    }

    private static void cleanupTree(Path root, Exception original) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException failure) {
                                original.addSuppressed(failure);
                            }
                        });
            }
        } catch (IOException failure) {
            original.addSuppressed(failure);
        }
    }

    private record EncodedEdit(
            int objectId,
            ObjectDefinitionRawView preview,
            byte[] payload) {
        private EncodedEdit {
            preview = Objects.requireNonNull(preview, "preview");
            payload = Objects.requireNonNull(payload, "payload").clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    public record BuildResult(
            Path outputCache,
            List<Integer> objectIds,
            long encodedBytes) {
        public BuildResult {
            outputCache = Objects.requireNonNull(outputCache, "outputCache")
                    .toAbsolutePath().normalize();
            objectIds = List.copyOf(objectIds);
            if (encodedBytes < 0) {
                throw new IllegalArgumentException("Encoded byte count cannot be negative");
            }
        }

        public int definitionCount() {
            return objectIds.size();
        }
    }
}
