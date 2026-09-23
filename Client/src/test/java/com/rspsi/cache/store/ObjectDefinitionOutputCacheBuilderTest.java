package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.displee.compress.CompressionType;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import dev.openrune.definition.codec.ObjectCodec;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.openrune.cache.ConfigTypeKt.OBJECT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionOutputCacheBuilderTest {
    private static final int REVISION = 240;
    private static final int OBJECT_ID = 1276;

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsVerifiedOutputWithoutMutatingSource() throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        Path output = temporaryDirectory.resolve("built-cache");
        ObjectType original = seedCache(source, "Copper rocks");
        byte[] sourceBytes = encode(original);

        ObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        transaction.setField("name",
                ObjectDefinitionEditValue.stringValue("Studio copper rocks"));
        transaction.setField("sizeX",
                ObjectDefinitionEditValue.intValue(2));
        transaction.setField("animationId",
                ObjectDefinitionEditValue.intValue(8321));
        transaction.putParam(451,
                ObjectDefinitionEditValue.intValue(12));
        byte[] expected = transaction.encodeValidated();

        ObjectDefinitionOutputCacheBuilder.BuildResult result =
                ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                        source, output, REVISION, java.util.List.of(transaction));

        assertEquals(output.toAbsolutePath().normalize(), result.outputCache());
        assertEquals(java.util.List.of(OBJECT_ID), result.objectIds());
        assertEquals(1, result.definitionCount());
        assertEquals(expected.length, result.encodedBytes());
        assertTrue(Files.isDirectory(output));

        try (OpenRuneCacheStore sourceStore = OpenRuneCacheStore.open(source)) {
            assertArrayEquals(sourceBytes,
                    sourceStore.readObjectDefinitionPayload(OBJECT_ID),
                    "the read-only source cache must remain byte-identical");
        }

        try (OpenRuneCacheStore outputStore = OpenRuneCacheStore.open(output)) {
            assertArrayEquals(expected,
                    outputStore.readObjectDefinitionPayload(OBJECT_ID));
            ObjectType decoded = new ObjectCodec(REVISION).loadData(
                    OBJECT_ID,
                    outputStore.readObjectDefinitionPayload(OBJECT_ID));
            assertEquals(transaction.preview(),
                    OpenRuneDefinitionProvider.toRawView(decoded));
        }
    }

    @Test
    void rejectsExistingOutputInsteadOfOverwritingIt() throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        Path output = temporaryDirectory.resolve("existing-output");
        ObjectType original = seedCache(source, "Tree");
        Files.createDirectories(output);
        Path marker = output.resolve("keep.txt");
        Files.writeString(marker, "do not overwrite");

        ObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        transaction.setField("name",
                ObjectDefinitionEditValue.stringValue("Edited tree"));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                        source, output, REVISION, java.util.List.of(transaction)));

        assertTrue(failure.getMessage().contains("already exists"));
        assertEquals("do not overwrite", Files.readString(marker));
    }

    @Test
    void rejectsTransactionFromAnotherSourceBeforeCreatingOutput() throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        Path output = temporaryDirectory.resolve("built-cache");
        seedCache(source, "Copper rocks");

        ObjectType other = objectType("Completely different source");
        ObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(other, REVISION);
        transaction.setField("sizeY",
                ObjectDefinitionEditValue.intValue(3));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                        source, output, REVISION, java.util.List.of(transaction)));

        assertTrue(failure.getMessage().contains("does not match"));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsOutputNestedInsideSource() throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        ObjectType original = seedCache(source, "Tree");
        Path output = source.resolve("generated-output");

        ObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);
        transaction.setField("name",
                ObjectDefinitionEditValue.stringValue("Edited tree"));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                        source, output, REVISION, java.util.List.of(transaction)));

        assertTrue(failure.getMessage().contains("inside the source"));
        assertFalse(Files.exists(output));
    }

    @Test
    void ignoresCleanTransactionsButRequiresAtLeastOneDirtyDefinition()
            throws IOException {
        Path source = temporaryDirectory.resolve("source-cache");
        Path output = temporaryDirectory.resolve("built-cache");
        ObjectType original = seedCache(source, "Tree");

        ObjectDefinitionEditTransaction clean =
                new OpenRuneObjectDefinitionEditTransaction(original, REVISION);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                        source, output, REVISION, java.util.List.of(clean)));

        assertTrue(failure.getMessage().contains("dirty"));
        assertFalse(Files.exists(output));
    }

    private static ObjectType seedCache(Path directory, String name)
            throws IOException {
        Files.createDirectories(directory);
        for (String file : new String[]{"main_file_cache.dat2", "main_file_cache.idx255"}) {
            Files.createFile(directory.resolve(file));
        }

        CacheLibrary library = CacheLibrary.create(directory.toString());
        try {
            // CacheDelegate expects a contiguous index table. Index 2 is configs
            // and index 5 is maps, matching the minimum production profile used
            // by the existing writable round-trip fixture.
            for (int index = 0; index <= 5; index++) {
                library.createIndex(
                        CompressionType.NONE,
                        6,
                        0,
                        index == 5,
                        true,
                        true,
                        true,
                        true,
                        index);
            }

            ObjectType object = objectType(name);
            byte[] payload = encode(object);
            library.put(
                    com.rspsi.cache.OsrsCacheIndexLayout.CONFIGS,
                    OBJECT,
                    OBJECT_ID,
                    payload);
            library.update();

            // Production edit transactions always originate from decoded cache
            // definitions. Decode the seeded payload too so OpenRune's default
            // normalization cannot make the fixture look like a different
            // source definition.
            return new ObjectCodec(REVISION).loadData(OBJECT_ID, payload);
        } finally {
            library.close();
        }
    }

    private static ObjectType objectType(String name) {
        ObjectTypeBuilder builder = new ObjectTypeBuilder(OBJECT_ID);
        builder.setName(name);
        builder.setSizeX(1);
        builder.setSizeY(1);
        builder.setAnimationId(-1);
        java.util.TreeMap<Integer, Object> params = new java.util.TreeMap<>();
        params.put(100, "original");
        params.put(101, 7);
        builder.setParams(params);
        return builder.build();
    }

    private static byte[] encode(ObjectType definition) {
        ObjectCodec codec = new ObjectCodec(REVISION);
        ByteBuf buffer = Unpooled.buffer(256);
        try {
            codec.encode(buffer, definition);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }
}
