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
import java.util.UUID;

/**
 * Publishes validated object-definition snapshots without ever opening the
 * loaded source cache writable.
 *
 * <p>New outputs are cloned from the source into a sibling staging directory.
 * Existing outputs are cloned from the selected output into staging after the
 * expected prior published definitions are verified. Staged writes are closed,
 * reopened read-only, and byte/semantic/canonical verified before publication.
 * Existing-output updates retain a rollback copy until the verified staging
 * directory has replaced the selected output.</p>
 */
public final class ObjectDefinitionOutputCacheBuilder {

    private ObjectDefinitionOutputCacheBuilder() {
    }

    /**
     * Captures immutable canonical payloads from the current unpublished
     * transaction previews. Call this on the editor thread before dispatching
     * filesystem work so later edits cannot race an in-progress build.
     */
    public static BuildPlan plan(
            Collection<? extends ObjectDefinitionEditTransaction> transactions) {
        Objects.requireNonNull(transactions, "transactions");

        Map<Integer, ObjectDefinitionEditTransaction> unpublished =
                new LinkedHashMap<>();
        for (ObjectDefinitionEditTransaction transaction : transactions) {
            ObjectDefinitionEditTransaction checked =
                    Objects.requireNonNull(transaction, "transaction");
            if (!checked.hasUnpublishedChanges()) {
                continue;
            }
            ObjectDefinitionEditTransaction previous = unpublished.putIfAbsent(
                    checked.id(), checked);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate object definition transaction for id " + checked.id());
            }
        }
        if (unpublished.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one unpublished object definition transaction is required");
        }

        List<PlannedObjectDefinition> definitions =
                new ArrayList<>(unpublished.size());
        unpublished.entrySet().stream()
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
                    ObjectDefinitionRawView expectedOutputBase =
                            transaction.publishedPreview()
                                    .orElse(transaction.original());
                    definitions.add(new PlannedObjectDefinition(
                            transaction.id(),
                            transaction.original(),
                            expectedOutputBase,
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

        validateCommonArguments(sourceCache, outputCache, revision, plan);

        Path source = sourceCache.toAbsolutePath().normalize();
        Path output = outputCache.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "Source cache directory does not exist: " + source);
        }
        Path sourceReal = source.toRealPath();

        validateNewOutputPath(source, sourceReal, output);

        List<PlannedObjectDefinition> edits =
                validatePlanAgainstSource(sourceReal, revision, plan);
        Path outputParent = requireParent(output);
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
            moveDirectory(staging, output);

            return buildResult(output, edits);
        } catch (IOException | RuntimeException failure) {
            cleanupTree(staging, failure);
            throw failure;
        }
    }

    /** Convenience overload for updating an existing output cache. */
    public static BuildResult updateExistingOutput(
            Path sourceCache,
            Path outputCache,
            int revision,
            Collection<? extends ObjectDefinitionEditTransaction> transactions)
            throws IOException {
        return updateExistingOutput(
                sourceCache,
                outputCache,
                revision,
                plan(transactions));
    }

    /**
     * Transactionally updates an explicitly selected existing output cache.
     *
     * <p>The selected output is never edited in place. Its definitions that are
     * about to change must match the expected prior publication baseline
     * captured in the plan. The complete output is then cloned to staging,
     * changed and verified there. Publication moves the old output aside as a
     * rollback backup, moves the verified staging directory into place, and
     * only then removes the backup.</p>
     *
     * @throws IllegalArgumentException if the output is missing, aliases the
     *                                  source, nests with the source, is a
     *                                  symbolic link, or does not match the
     *                                  expected prior publication baseline
     * @throws IOException              if staging/copy/replacement operations fail
     */
    public static BuildResult updateExistingOutput(
            Path sourceCache,
            Path outputCache,
            int revision,
            BuildPlan plan)
            throws IOException {

        validateCommonArguments(sourceCache, outputCache, revision, plan);

        Path source = sourceCache.toAbsolutePath().normalize();
        Path output = outputCache.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "Source cache directory does not exist: " + source);
        }
        if (!Files.isDirectory(output)) {
            throw new IllegalArgumentException(
                    "Existing output cache directory does not exist: " + output);
        }
        if (Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException(
                    "Existing output cache cannot be a symbolic link: " + output);
        }

        Path sourceReal = source.toRealPath();
        Path outputReal = output.toRealPath();
        validateExistingOutputPath(source, sourceReal, output, outputReal);

        List<PlannedObjectDefinition> edits =
                validatePlanAgainstSource(sourceReal, revision, plan);
        validatePlanAgainstExistingOutput(outputReal, revision, edits);

        Path outputParent = requireParent(output);
        Path staging = Files.createTempDirectory(
                outputParent,
                "." + output.getFileName() + "-staging-");
        try {
            copyCacheDirectory(outputReal, staging);
            writeStagedDefinitions(staging, edits);
            verifyCache(staging, revision, edits);

            replaceExistingOutput(staging, output);
            return buildResult(output, edits);
        } catch (IOException | RuntimeException failure) {
            cleanupTree(staging, failure);
            throw failure;
        }
    }

    private static void validateCommonArguments(
            Path sourceCache,
            Path outputCache,
            int revision,
            BuildPlan plan) {
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

    private static void validatePlanAgainstExistingOutput(
            Path output,
            int revision,
            List<PlannedObjectDefinition> edits) {

        try (OpenRuneCacheStore outputStore = OpenRuneCacheStore.open(output)) {
            for (PlannedObjectDefinition edit : edits) {
                byte[] outputPayload =
                        outputStore.readObjectDefinitionPayload(edit.objectId());
                if (outputPayload == null) {
                    throw new IllegalArgumentException(
                            "Existing output cache does not contain object definition "
                                    + edit.objectId());
                }

                ObjectDefinitionRawView outputRaw =
                        outputStore.decodeObjectDefinitionPayload(
                                edit.objectId(), outputPayload, revision);
                if (!edit.expectedOutputBase().equals(outputRaw)) {
                    throw new IllegalArgumentException(
                            "Existing output cache has an unexpected prior value for object "
                                    + edit.objectId()
                                    + "; refusing to overwrite a stale or unrelated cache");
                }
            }
        }
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

    private static void validateNewOutputPath(
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
                    "Output cache already exists; use updateExistingOutput for an explicit update: "
                            + output);
        }

        // Reject the obvious lexical nesting before creating any directories.
        if (output.startsWith(sourceAbsolute)) {
            throw new IllegalArgumentException(
                    "Output cache cannot be created inside the source cache");
        }

        Path parent = requireParent(output);
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

    private static void validateExistingOutputPath(
            Path sourceAbsolute,
            Path sourceReal,
            Path outputAbsolute,
            Path outputReal) throws IOException {

        if (sourceAbsolute.equals(outputAbsolute)
                || Files.isSameFile(sourceReal, outputReal)) {
            throw new IllegalArgumentException(
                    "Source and output cache paths must differ");
        }
        if (outputReal.startsWith(sourceReal)) {
            throw new IllegalArgumentException(
                    "Existing output cache cannot be inside the source cache");
        }
        if (sourceReal.startsWith(outputReal)) {
            throw new IllegalArgumentException(
                    "Existing output cache cannot contain the source cache");
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Output cache must have a parent directory");
        }
        return parent;
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

    private static void replaceExistingOutput(Path staging, Path output)
            throws IOException {
        Path backup = output.resolveSibling(
                "." + output.getFileName() + "-rollback-" + UUID.randomUUID());

        moveDirectory(output, backup);
        try {
            moveDirectory(staging, output);
        } catch (IOException publishFailure) {
            IOException failure = new IOException(
                    "Failed to replace existing output cache; attempting rollback to "
                            + output,
                    publishFailure);
            try {
                if (Files.exists(output)) {
                    throw new IOException(
                            "Replacement failed but output path already exists; rollback copy retained at "
                                    + backup);
                }
                moveDirectory(backup, output);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }

        cleanupTreeQuietly(backup);
    }

    private static void moveDirectory(Path source, Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static BuildResult buildResult(
            Path output,
            List<PlannedObjectDefinition> edits) {
        long bytes = edits.stream().mapToLong(edit -> edit.payload().length).sum();
        return new BuildResult(
                output,
                edits.stream().map(PlannedObjectDefinition::objectId).toList(),
                bytes);
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

    private static void cleanupTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Publication already succeeded. A leftover
                                // rollback directory is safer than reporting
                                // the verified output as failed.
                            }
                        });
            }
        } catch (IOException ignored) {
            // See above: cleanup cannot invalidate a successful publication.
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
            ObjectDefinitionRawView expectedOutputBase,
            ObjectDefinitionRawView preview,
            byte[] payload) {
        public PlannedObjectDefinition {
            if (objectId < 0) {
                throw new IllegalArgumentException(
                        "Object definition id cannot be negative");
            }
            original = Objects.requireNonNull(original, "original");
            expectedOutputBase =
                    Objects.requireNonNull(expectedOutputBase, "expectedOutputBase");
            preview = Objects.requireNonNull(preview, "preview");
            payload = Objects.requireNonNull(payload, "payload").clone();
            if (original.id() != objectId
                    || expectedOutputBase.id() != objectId
                    || preview.id() != objectId) {
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
                throw new IllegalArgumentException(
                        "Encoded byte count cannot be negative");
            }
        }

        public int definitionCount() {
            return objectIds.size();
        }
    }
}
