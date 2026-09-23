package com.rspsi.cache.store;

import com.rspsi.cache.OsrsCacheIndexLayout;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Publishes validated object-definition transaction snapshots into an explicit
 * OpenRune output cache without ever making the live source cache writable.
 *
 * <p>Publishing is deliberately copy-on-build. The selected source cache, or
 * an existing valid output cache, is copied to a sibling staging directory.
 * Only the staged cache is opened through {@link OpenRuneCacheStore#openWritable(Path)}.
 * The staged result is then closed, reopened read-only, byte-checked and
 * definition-decoded before it is promoted to the requested output path.</p>
 *
 * <p>This favors correctness and recoverability over copy performance for the
 * first definition persistence path. OpenRune incremental packing can replace
 * the staging implementation later without changing the immutable
 * {@link BuildPlan} contract.</p>
 */
public final class ObjectDefinitionOutputPublisher {
    /** OSRS CONFIG archive containing loc/object definitions. */
    static final int OBJECT_CONFIG_ARCHIVE = 6;

    private ObjectDefinitionOutputPublisher() {
    }

    /**
     * Snapshots the current dirty transaction state into immutable payloads.
     *
     * <p>Call this on the editor thread before dispatching file I/O so later UI
     * edits cannot race the bytes being published.</p>
     */
    public static BuildPlan plan(
            Collection<? extends ObjectDefinitionEditTransaction> transactions) {
        Objects.requireNonNull(transactions, "transactions");
        List<DefinitionPayload> payloads = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (ObjectDefinitionEditTransaction transaction : transactions) {
            Objects.requireNonNull(transaction, "transaction");
            if (!transaction.dirty()) {
                continue;
            }
            int id = transaction.id();
            if (!ids.add(id)) {
                throw new IllegalArgumentException(
                        "Duplicate object definition transaction: " + id);
            }
            byte[] encoded = transaction.encodeValidated();
            ObjectDefinitionRawView expected = transaction.preview();
            if (expected.id() != id) {
                throw new IllegalStateException(
                        "Object transaction preview id " + expected.id()
                                + " does not match transaction id " + id);
            }
            payloads.add(new DefinitionPayload(id, encoded, expected));
        }
        payloads.sort(Comparator.comparingInt(DefinitionPayload::id));
        return new BuildPlan(payloads);
    }

    /**
     * Builds and verifies an explicit output cache.
     *
     * @param sourceCache read-only source cache selected by Studio
     * @param outputCache separate output cache path to create or replace
     * @param revision    decoder profile used by the source session
     * @param plan        immutable payload snapshot created by {@link #plan(Collection)}
     */
    public static BuildResult publish(Path sourceCache, Path outputCache,
                                      int revision, BuildPlan plan) throws IOException {
        Objects.requireNonNull(sourceCache, "sourceCache");
        Objects.requireNonNull(outputCache, "outputCache");
        Objects.requireNonNull(plan, "plan");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        if (plan.payloads().isEmpty()) {
            throw new IllegalArgumentException(
                    "Object definition output plan contains no dirty definitions");
        }

        Path source = sourceCache.toAbsolutePath().normalize();
        Path output = outputCache.toAbsolutePath().normalize();
        validatePaths(source, output);

        int sourceRevision = OpenRuneCacheStore.detectRevision(source);
        if (sourceRevision != revision) {
            throw new IllegalArgumentException(
                    "Source cache revision " + sourceRevision
                            + " does not match requested revision " + revision);
        }

        Path base = source;
        boolean outputExisted = Files.exists(output);
        if (outputExisted) {
            if (!Files.isDirectory(output)) {
                throw new IllegalArgumentException(
                        "Output cache path is not a directory: " + output);
            }
            if (!directoryEmpty(output)) {
                int outputRevision = OpenRuneCacheStore.detectRevision(output);
                if (outputRevision != revision) {
                    throw new IllegalArgumentException(
                            "Existing output cache revision " + outputRevision
                                    + " does not match source revision " + revision);
                }
                base = output;
            }
        }

        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Output cache must have a parent directory: " + output);
        }
        Files.createDirectories(parent);

        String prefix = "." + safeFileName(output) + "-studio-staging-";
        Path staging = Files.createTempDirectory(parent, prefix);
        boolean promoted = false;
        try {
            copyDirectoryContents(base, staging);
            writePlan(staging, plan);
            verifyPlan(staging, revision, plan);
            promote(staging, output, outputExisted);
            promoted = true;
        } finally {
            if (!promoted && Files.exists(staging)) {
                deleteDirectory(staging);
            }
        }

        long bytes = plan.payloads().stream()
                .mapToLong(payload -> payload.data().length)
                .sum();
        return new BuildResult(output, plan.payloads().size(),
                plan.payloads().stream().map(DefinitionPayload::id).toList(), bytes);
    }

    private static void writePlan(Path staging, BuildPlan plan) {
        try (CacheStore store = CacheStoreFactory.openRuneWritable(staging)) {
            if (!store.capabilities().writable()) {
                throw new IllegalStateException(
                        "Selected OpenRune output adapter is not writable");
            }
            int[] objectFiles = store.fileIds(
                    OsrsCacheIndexLayout.CONFIGS, OBJECT_CONFIG_ARCHIVE);
            Set<Integer> existing = Arrays.stream(objectFiles)
                    .boxed()
                    .collect(java.util.stream.Collectors.toSet());
            for (DefinitionPayload payload : plan.payloads()) {
                if (!existing.contains(payload.id())) {
                    throw new IllegalStateException(
                            "Output cache is missing source object definition "
                                    + payload.id() + " in CONFIG archive "
                                    + OBJECT_CONFIG_ARCHIVE);
                }
                store.write(OsrsCacheIndexLayout.CONFIGS, OBJECT_CONFIG_ARCHIVE,
                        payload.id(), payload.data());
            }
            store.flush();
        }
    }

    private static void verifyPlan(Path staging, int revision, BuildPlan plan) {
        try (OpenRuneCacheStore reopened = CacheStoreFactory.openOsrs(staging)) {
            var definitions = reopened.definitionProvider(revision);
            for (DefinitionPayload payload : plan.payloads()) {
                byte[] actualBytes = reopened.read(
                        OsrsCacheIndexLayout.CONFIGS, OBJECT_CONFIG_ARCHIVE, payload.id());
                if (!Arrays.equals(payload.data(), actualBytes)) {
                    throw new IllegalStateException(
                            "Written object payload failed byte verification: "
                                    + payload.id());
                }
                ObjectDefinitionRawView actual = definitions.objectRaw(payload.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Written object definition could not be decoded: "
                                        + payload.id()));
                if (!payload.expected().equals(actual)) {
                    throw new IllegalStateException(
                            "Written object definition failed decoded verification: "
                                    + payload.id());
                }
            }
        }
    }

    private static void validatePaths(Path source, Path output) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "Source cache directory does not exist: " + source);
        }
        Path realSource = source.toRealPath();
        Path comparableOutput = Files.exists(output)
                ? output.toRealPath()
                : canonicalFuturePath(output);
        if (realSource.equals(comparableOutput)) {
            throw new IllegalArgumentException(
                    "Source and output cache paths must differ");
        }
        if (comparableOutput.startsWith(realSource)
                || realSource.startsWith(comparableOutput)) {
            throw new IllegalArgumentException(
                    "Source and output cache directories must not contain one another");
        }
    }

    private static Path canonicalFuturePath(Path output) throws IOException {
        Path parent = output.getParent();
        if (parent == null) {
            return output;
        }
        Files.createDirectories(parent);
        return parent.toRealPath().resolve(output.getFileName()).normalize();
    }

    private static boolean directoryEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static void copyDirectoryContents(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path current : paths.toList()) {
                Path relative = source.relativize(current);
                Path destination = target.resolve(relative);
                if (relative.toString().isEmpty()) {
                    continue;
                }
                if (Files.isSymbolicLink(current)) {
                    throw new IOException(
                            "Cache staging refuses symbolic links: " + current);
                }
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(current)) {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(current, destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException(
                            "Unsupported cache entry while staging: " + current);
                }
            }
        }
    }

    private static void promote(Path staging, Path output,
                                boolean outputExisted) throws IOException {
        if (!outputExisted) {
            moveDirectory(staging, output);
            return;
        }

        Path parent = output.getParent();
        Path backup = Files.createTempDirectory(
                parent, "." + safeFileName(output) + "-studio-backup-");
        Files.delete(backup);

        moveDirectory(output, backup);
        boolean installed = false;
        try {
            moveDirectory(staging, output);
            installed = true;
        } finally {
            if (!installed && Files.exists(backup) && !Files.exists(output)) {
                moveDirectory(backup, output);
            }
        }
        if (Files.exists(backup)) {
            deleteDirectory(backup);
        }
    }

    private static void moveDirectory(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to);
        }
    }

    private static void deleteDirectory(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            IOException[] failure = new IOException[1];
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    if (failure[0] == null) {
                        failure[0] = exception;
                    } else {
                        failure[0].addSuppressed(exception);
                    }
                }
            });
            if (failure[0] != null) {
                throw failure[0];
            }
        }
    }

    private static String safeFileName(Path output) {
        Path fileName = output.getFileName();
        String value = fileName == null ? "cache" : fileName.toString();
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "cache" : safe;
    }

    /** Immutable, already-validated definition bytes ready for filesystem I/O. */
    public record BuildPlan(List<DefinitionPayload> payloads) {
        public BuildPlan {
            payloads = List.copyOf(payloads == null ? List.of() : payloads);
        }
    }

    /** One immutable object-definition payload plus its decoded expectation. */
    public static final class DefinitionPayload {
        private final int id;
        private final byte[] data;
        private final ObjectDefinitionRawView expected;

        private DefinitionPayload(int id, byte[] data,
                                  ObjectDefinitionRawView expected) {
            if (id < 0) {
                throw new IllegalArgumentException(
                        "Object definition id cannot be negative");
            }
            this.id = id;
            this.data = Objects.requireNonNull(data, "data").clone();
            this.expected = Objects.requireNonNull(expected, "expected");
        }

        public int id() {
            return id;
        }

        public byte[] data() {
            return data.clone();
        }

        public ObjectDefinitionRawView expected() {
            return expected;
        }
    }

    public record BuildResult(Path outputCache, int definitionsWritten,
                              List<Integer> objectIds, long encodedBytes) {
        public BuildResult {
            outputCache = Objects.requireNonNull(
                    outputCache, "outputCache").toAbsolutePath().normalize();
            objectIds = List.copyOf(objectIds == null ? List.of() : objectIds);
            if (definitionsWritten < 0 || encodedBytes < 0) {
                throw new IllegalArgumentException(
                        "Build result counts cannot be negative");
            }
            if (definitionsWritten != objectIds.size()) {
                throw new IllegalArgumentException(
                        "Definition count must match object id list");
            }
        }
    }
}
