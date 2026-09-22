package com.rspsi.cache.store;

import dev.openrune.definition.constants.ConstantProvider;
import dev.openrune.filesystem.Cache;
import dev.openrune.filesystem.Compression;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneSymbolicNameProviderTest {
    @Test
    void readsEmbeddedObjectGameValsFromTheSelectedCache() {
        OpenRuneSymbolicNameProvider names =
                new OpenRuneSymbolicNameProvider(new FakeGameValCache(), 240);

        assertEquals(Optional.of("objects.castle_wall"), names.name("object", 12));
        assertTrue(names.name("object", 13).isEmpty());
    }

    @Test
    void translatesLoadedReverseMappingsIntoNeutralOptionalNames() {
        ConstantProvider constants = ConstantProvider.INSTANCE;
        Map<String, Map<String, Integer>> previous = new HashMap<>(constants.getMappings());
        try {
            constants.setMappings(Map.of(
                    "objects", Map.of("objects.castle_wall", 12),
                    "overlays", Map.of("overlays.grass", 4)));

            OpenRuneSymbolicNameProvider names = new OpenRuneSymbolicNameProvider();
            assertEquals(Optional.of("objects.castle_wall"), names.name("object", 12));
            assertEquals(Optional.of("overlays.grass"), names.name("overlay", 4));
            assertTrue(names.name("npc", 12).isEmpty());
        } finally {
            constants.setMappings(previous);
        }
    }

    private static final class FakeGameValCache implements Cache {
        @Override public byte[] getVersionTable() { return new byte[0]; }
        @Override public int indexCount() { return 25; }
        @Override public boolean exists(int id) { return id == 24; }
        @Override public void createIndex(Compression c, int v, int r, boolean n, boolean w,
                                          boolean l, boolean ch, boolean wr, int id) { }
        @Override public int[] indices() { return new int[]{24}; }
        @Override public byte[] sector(int i, int a) { return null; }
        @Override public int[] archives(int i) { return i == 24 ? new int[]{6} : new int[0]; }
        @Override public int archiveCount(int i) { return archives(i).length; }
        @Override public int lastArchiveId(int i) { return i == 24 ? 6 : -1; }
        @Override public int archiveId(int i, int h) { return -1; }
        @Override public int archiveId(int i, String n) { return -1; }
        @Override public int[] files(int i, int a) {
            return i == 24 && a == 6 ? new int[]{12} : new int[0];
        }
        @Override public int fileCount(int i, int a) { return files(i, a).length; }
        @Override public int lastFileId(int i, int a) { return i == 24 && a == 6 ? 12 : -1; }
        @Override public byte[][] fileData(int i, int a) { return null; }
        @Override public byte[][] fileData(int i, int a, int[] x) { return null; }
        @Override public byte[] data(int i, int a, int f, int[] x) {
            return i == 24 && a == 6 && f == 12
                    ? "castle_wall".getBytes(StandardCharsets.UTF_8)
                    : null;
        }
        @Override public byte[] data(int i, String a, int[] x) { return null; }
        @Override public int crc(int i) { return 0; }
        @Override public int crc(int i, int a) { return 0; }
        @Override public void write(int i, int a, int f, byte[] d, int[] x) { }
        @Override public void write(int i, int a, byte[] d, int[] x) { }
        @Override public void write(int i, String a, byte[] d, int[] x) { }
        @Override public void remove(int i, int a, int f) { }
        @Override public void remove(int i, int a) { }
        @Override public boolean update() { return false; }
        @Override public void close() { }
    }

}
