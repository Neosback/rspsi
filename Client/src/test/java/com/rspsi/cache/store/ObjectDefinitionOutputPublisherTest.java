package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.displee.compress.CompressionType;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionOutputPublisherTest {
    private static final int REVISION = 240;
    private static final int OBJECT_ID = 321;

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesToSeparateCacheAndVerifiesReadOnlyReopen() throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        ObjectType original = sourceDefinition();
        seedCache(source, original);
        String sourceBefore = directoryDigest(source);

        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        transaction.setField("name",
                ObjectDefinitionEditValue.stringValue("Published chest"));
        transaction.setField("sizeX", ObjectDefinitionEditValue.intValue(3));
        transaction.putParam(100,
                ObjectDefinitionEditValue.stringValue("studio"));

        ObjectDefinitionOutputPublisher.BuildPlan plan =
                ObjectDefinitionOutputPublisher.plan(List.of(transaction));
        Path output = temporaryDirectory.resolve("output-cache");

        ObjectDefinitionOutputPublisher.BuildResult result =
                ObjectDefinitionOutputPublisher.publish(
                        source, output, REVISION, plan);

        assertEquals(output.toAbsolutePath().normalize(), result.outputCache());
        assertEquals(1, result.definitionsWritten());
        assertEquals(List.of(OBJECT_ID), result.objectIds());
        assertTrue(result.encodedBytes() > 0);
        assertEquals(sourceBefore, directoryDigest(source),
                "publishing must not mutate the selected source cache");

        try (OpenRuneCacheStore sourceStore = CacheStoreFactory.openOsrs(source)) {
            var sourceObject = sourceStore.definitionProvider(REVISION)
                    .objectRaw(OBJECT_ID).orElseThrow();
            assertEquals("Old chest", field(sourceObject, "name"));
            assertEquals("1", field(sourceObject, "sizeX"));
            assertFalse(sourceObject.params().stream()
                    .anyMatch(param -> param.id() == 100));
        }

        try (OpenRuneCacheStore outputStore = CacheStoreFactory.openOsrs(output)) {
            var published = outputStore.definitionProvider(REVISION)
                    .objectRaw(OBJECT_ID).orElseThrow();
            assertEquals(transaction.preview(), published);
            assertEquals("Published chest", field(published, "name"));
            assertEquals("3", field(published, "sizeX"));
            assertEquals("studio", published.params().stream()
                    .filter(param -> param.id() == 100)
                    .findFirst().orElseThrow().value());
        }
    }

    @Test
    void rejectsSourceAsOutputBeforeAnyWrite() throws IOException {
        Path source = temporaryDirectory.resolve("same-cache");
        ObjectType original = sourceDefinition();
        seedCache(source, original);
        String before = directoryDigest(source);

        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        transaction.setField("name",
                ObjectDefinitionEditValue.stringValue("Must not write"));

        var plan = ObjectDefinitionOutputPublisher.plan(List.of(transaction));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectDefinitionOutputPublisher.publish(
                        source, source, REVISION, plan));
        assertEquals(before, directoryDigest(source));
    }

    @Test
    void planSkipsCleanTransactionsAndRejectsEmptyPublish() throws IOException {
        Path source = temporaryDirectory.resolve("clean-source");
        ObjectType original = sourceDefinition();
        seedCache(source, original);

        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        var plan = ObjectDefinitionOutputPublisher.plan(List.of(transaction));
        assertTrue(plan.payloads().isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> ObjectDefinitionOutputPublisher.publish(
                        source,
                        temporaryDirectory.resolve("clean-output"),
                        REVISION,
                        plan));
    }

    private static ObjectType sourceDefinition() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder(OBJECT_ID);
        builder.setName("Old chest");
        builder.setSizeX(1);
        builder.setSizeY(2);
        builder.setHollow(false);
        builder.setParams(new HashMap<>(Map.of(7, 12)));
        return builder.build();
    }

    private static void seedCache(Path path, ObjectType definition) throws IOException {
        Files.createDirectories(path);
        Files.createFile(path.resolve("main_file_cache.dat2"));
        Files.createFile(path.resolve("main_file_cache.idx255"));

        CacheLibrary library = CacheLibrary.create(path.toString());
        try {
            // Keep indices contiguous because OpenRune CacheDelegate creates
            // its version table from the contiguous OSRS index range.
            for (int index = 0; index <= 9; index++) {
                library.createIndex(
                        CompressionType.NONE, 6, 0,
                        index == 5, true, true, true, true, index);
            }

            OpenRuneObjectDefinitionEditTransaction clean =
                    new OpenRuneObjectDefinitionEditTransaction(
                            definition, REVISION);
            library.put(2,
                    ObjectDefinitionOutputPublisher.OBJECT_CONFIG_ARCHIVE,
                    definition.getId(),
                    clean.encodeValidated());
            // detectRevision requires a non-empty maps index. The publisher
            // never decodes this synthetic archive.
            library.put(5, "m50_50", new byte[]{0});
            library.update();
        } finally {
            library.close();
        }
    }

    private static String field(
            com.rspsi.cache.definition.ObjectDefinitionRawView raw,
            String name) {
        return raw.fields().stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static String directoryDigest(Path root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .sorted()
                        .toList()) {
                    byte[] relative = root.relativize(path).toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(Integer.BYTES)
                            .putInt(relative.length).array());
                    digest.update(relative);
                    byte[] data = Files.readAllBytes(path);
                    digest.update(ByteBuffer.allocate(Long.BYTES)
                            .putLong(data.length).array());
                    digest.update(data);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }
}
