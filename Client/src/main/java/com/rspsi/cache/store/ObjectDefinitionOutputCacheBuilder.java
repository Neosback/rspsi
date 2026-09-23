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
 * of validated object-definition snapshots.
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
     * Captures immutable canonical payloads from the current transaction
     * previews. Call this on the editor thread before dispatching filesystem
     * work so later edits cannot race an in-progress build.
     */
    public static BuildPlan plan(
            Collection<? extends ObjectDefinitionEditTransaction> transactions) {
        Objects.requireNonNull(transactions, "transactions");

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

        List<PlannedObjectDefinition> definitions = new ArrayList<>(dirty.size());
        dirty.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ObjectDefinitionEditTransaction transaction = entry.getValue();
                    byte[] encoded = transaction.encodeValidated();
                    ObjectDefinitionRawView preview = transaction.preview();
                    if (preview.id() != transaction.id()) {
                        throw new IllegalStateException(
                                "Object definition preview id " + preview.id()
                                        + " does not match transaction " + transaction.id());
                    }
                    definitions.add(new PlannedObjectDefinition(
                            transaction.id(),
                            transaction.original(),
                            preview,
                            encoded));
                });
        return new BuildPlan(definitions);
    }

    /** Convenience overload for headless callers that do not need async planning. */
    public static BuildResult buildNewOutput(
            Path sourceCache,
            Path outputCache,
            int revision,
            Collection<? extends ObjectDefinitionEditTransaction> transactions)
            throws IOException {
        return buildNewOutput(
                sourceCache,
                outputCache,
                revision,
                plan(transactions));
    }

    /**
     * Creates a new output cache containing the source cache plus all
     * definitions captured by an immutable {@link BuildPlan}.
     *
     * @throws IllegalArgumentException if the output path aliases/nests under
     *                                  the source, already exists, or the plan
     *                                  does not belong to the selected source
     * @throws IOException              if staging/copy/move operations fail
     */
    public static BuildResult buildNewOutput(
            Path sourceCache,
            Path outputCache,
            int revision,
            BuildPlan plan)
            throws IOException {

        Objects.requireNonNull(sourceCache, "sourceCache");
        Objects.requireNonNull(outputCache, "outputCache");
        Objects.requireNonNull(plan, "plan");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }
        if (plan.definitions().isEmpty()) {
            throw new IllegalArgumentException(
                    "Object definition output plan cannot be empty");
        }

        Path source = sourceCache.toAbsolutePath().normalize();
        Path output = outputCache.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source cache directory does not exist: " + source);
        }
        Path sourceReal = source.toRealPath();

        validateOutputPath(source, sourceReal, output);

        List<PlannedObjectDefinition> edits =
                validatePlanAgainstSource(sourceReal, revision, plan);
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
                    edits.stream().map(PlannedObjectDefinition::objectId).toList(),
                    bytes);
        } catch (IOException | RuntimeException failure) {
            cleanupTree(staging, failure);
            throw failure;
        }
    }

    private static List<PlannedObjectDefinition> validatePlanAgainstSource(
            Path source,
            int revision,
            BuildPlan plan) {

        try (OpenRuneCacheStore sourceStore = OpenRuneCacheStore.open(source)) {
            for (PlannedObjectDefinition edit : plan.definitions()) {
                byte[] sourcePayload =
                        sourceStore.readObjectDefinitionPayload(edit.objectId());
                if (sourcePayload == null) {
                    throw new IllegalArgumentException(
                            "Source cache does not contain object definition "
                                    + edit.objectId());
                }

                ObjectDefinitionRawView sourceRaw =
                        sourceStore.decodeObjectDefinitionPayload(
                                edit.objectId(), sourcePayload, revision);
                if (!sourceRaw.equals(edit.original())) {
                    throw new IllegalArgumentException(
                            "Object definition transaction " + edit.objectId()
                                    + " does not match the selected source cache");
                }

                ObjectDefinitionRawView encodedRaw =
                        sourceStore.decodeObjectDefinitionPayload(
                                edit.objectId(), edit.payload(), revision);
                if (!edit.preview().equals(encodedRaw)) {
                    throw new IllegalStateException(
                            "Validated payload does not decode to the planned preview "
                                    + "for object " + edit.objectId());
                }
            }
        }
        return plan.definitions();
    }

    private static void writeStagedDefinitions(
            Path staging,
            List<PlannedObjectDefinition> edits) {
        try (OpenRuneCacheStore output = OpenRuneCacheStore.openWritable(staging)) {
            for (PlannedObjectDefinition edit : edits) {
                output.writeObjectDefinitionPayload(edit.objectId(), edit.payload());
            }
            output.flush();
        }
    }

    private static void verifyCache(
            Path cachePath,
            int revision,
            List<PlannedObjectDefinition> edits) {

        try (OpenRuneCacheStore reopened = OpenRuneCacheStore.open(cachePath)) {
            for (PlannedObjectDefinition edit : edits) {
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

    /** Immutable snapshot of all definition payloads included in one build. */
    public record BuildPlan(List<PlannedObjectDefinition> definitions) {
        public BuildPlan {
            definitions = List.copyOf(
                    Objects.requireNonNull(definitions, "definitions"));
            if (definitions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Object definition output plan cannot be empty");
            }
        }

        public int definitionCount() {
            return definitions.size();
        }
    }

    /** One immutable object definition captured before output I/O begins. */
    public record PlannedObjectDefinition(
            int objectId,
            ObjectDefinitionRawView original,
            ObjectDefinitionRawView preview,
            byte[] payload) {
        public PlannedObjectDefinition {
            if (objectId < 0) {
                throw new IllegalArgumentException(
                        "Object definition id cannot be negative");
            }
            original = Objects.requireNonNull(original, "original");
            preview = Objects.requireNonNull(preview, "preview");
            payload = Objects.requireNonNull(payload, "payload").clone();
            if (original.id() != objectId || preview.id() != objectId) {
                throw new IllegalArgumentException(
                        "Planned object definition views must match id " + objectId);
            }
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
