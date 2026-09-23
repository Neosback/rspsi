package com.rspsi.cache.store;

import com.rspsi.cache.OsrsCacheIndexLayout;
import com.rspsi.cache.definition.CachedSkeletalAnimationView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ModelSkeletalSkinView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.SkeletalRigView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import com.rspsi.editor.render.CachedSkeletalModelAnimation;
import dev.openrune.filesystem.Cache;
import dev.openrune.filesystem.Compression;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static dev.openrune.cache.ArchiveIndexKt.CONFIGS;
import static dev.openrune.cache.ConfigTypeKt.SEQUENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneCachedSkeletalDecoderTest {
    private static final int SEQUENCE_ID = 42;
    private static final int SKELETON_ID = 5;
    private static final int ANIMATION_ID = 0x00010002;

    @Test
    void decodesRawCachedSkeletalFixtureThroughModelTransform() throws IOException {
        FakeCache cache = new FakeCache();
        cache.put(CONFIGS, SEQUENCE, SEQUENCE_ID, sequenceBytes());
        cache.put(OsrsCacheIndexLayout.SKELETONS, SKELETON_ID, 0, skeletonBytes());
        cache.put(OsrsCacheIndexLayout.ANIMATIONS,
                ANIMATION_ID >>> 16, ANIMATION_ID & 0xFFFF, cachedAnimationBytes());

        OpenRuneDefinitionProvider provider = OpenRuneDefinitionProvider.load(cache, 240);

        SequenceDefinitionView sequence = provider.sequence(SEQUENCE_ID).orElseThrow();
        assertTrue(sequence.cachedSkeletal());
        assertEquals(ANIMATION_ID, sequence.skeletalId());
        assertEquals(10, sequence.skeletalRangeBegin());
        assertEquals(18, sequence.skeletalRangeEnd());
        assertEquals(8, sequence.cachedFrameCount());
        assertEquals(-7, sequence.animationHeightOffset());

        SkeletonDefinitionView skeleton = provider.skeleton(SKELETON_ID).orElseThrow();
        SkeletalRigView rig = skeleton.rig().orElseThrow();
        assertEquals(1, rig.boneCount());
        assertEquals(1, rig.poseCount());
        assertEquals(-1, rig.parentIndices()[0]);
        assertEquals(1.0f, rig.bindMatrix(0, 0)[0]);

        CachedSkeletalAnimationView animation =
                provider.cachedSkeletalAnimation(ANIMATION_ID).orElseThrow();
        assertEquals(SKELETON_ID, animation.skeletonId());
        assertEquals(0, animation.poseIndex());
        assertEquals(10.0f, animation.boneCurve(0, 3).valueAt(10));
        assertTrue(animation.hasAlphaTransforms());
        assertEquals(0.1f, animation.alphaCurve(0).valueAt(10));

        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{10},
                new int[]{-1})
                .withTriangleSkins(new int[]{2});
        ModelSkeletalSkinView skin = new ModelSkeletalSkinView(
                7,
                new int[][]{{0}, {0}, {0}},
                new int[][]{{255}, {255}, {255}});

        ModelGeometryView transformed = CachedSkeletalModelAnimation.apply(
                geometry, skin, skeleton, animation, 10);

        assertEquals(10, transformed.vertexPositions()[0]);
        assertEquals(74, transformed.vertexPositions()[3]);
        assertEquals(10, transformed.vertexPositions()[6]);
        assertEquals(35, transformed.triangleAlphas()[0]);
    }

    private static byte[] sequenceBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(13);
            out.writeInt(ANIMATION_ID);
            out.writeByte(15);
            out.writeShort(10);
            out.writeShort(18);
            out.writeByte(16);
            out.writeByte(-7);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] skeletonBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(1);      // one legacy transform group
            out.writeByte(5);      // alpha transform
            out.writeByte(1);      // one label
            out.writeByte(2);      // triangle-skin label 2

            out.writeShort(1);     // one cached-model bone
            out.writeByte(1);      // one bind pose
            out.writeShort(-1);    // root bone

            for (int index = 0; index < 16; index++) {
                out.writeFloat(index == 0 || index == 5 || index == 10 || index == 15
                        ? 1.0f : 0.0f);
            }
            out.writeFloat(0.0f);
            out.writeFloat(0.0f);
            out.writeFloat(0.0f);
        }
        return bytes.toByteArray();
    }

    private static byte[] cachedAnimationBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(1);              // cached animation format version
            out.writeShort(SKELETON_ID);
            out.writeShort(10);            // client reads but does not retain
            out.writeShort(18);            // client reads but does not retain
            out.writeByte(0);              // bind-pose index
            out.writeShort(2);             // two curves

            out.writeByte(1);              // bone curve
            out.writeByte(64);             // short-smart target 0
            out.writeByte(4);              // channel 4 => translation X
            writeConstantCurve(out, 10.0f);

            out.writeByte(4);              // alpha curve
            out.writeByte(64);             // short-smart transform target 0
            out.writeByte(1);              // alpha channel metadata
            writeConstantCurve(out, 0.1f);
        }
        return bytes.toByteArray();
    }

    private static void writeConstantCurve(DataOutputStream out, float value) throws IOException {
        out.writeShort(1);
        out.writeByte(0);       // interpolation metadata
        out.writeByte(0);       // constant extrapolation before
        out.writeByte(0);       // constant extrapolation after
        out.writeByte(0);       // non-Bezier
        out.writeShort(10);     // frame
        out.writeFloat(value);
        out.writeFloat(0.0f);   // incoming time
        out.writeFloat(0.0f);   // incoming value
        out.writeFloat(0.0f);   // outgoing time
        out.writeFloat(0.0f);   // outgoing value
    }

    private static final class FakeCache implements Cache {
        private final Map<String, byte[]> values = new HashMap<>();

        private void put(int index, int archive, int file, byte[] data) {
            values.put(key(index, archive, file), data);
        }

        private static String key(int index, int archive, int file) {
            return index + ":" + archive + ":" + file;
        }

        @Override public byte[] getVersionTable() { return new byte[0]; }
        @Override public int indexCount() { return 0; }
        @Override public boolean exists(int id) { return false; }
        @Override public void createIndex(Compression c, int v, int r, boolean n, boolean w,
                                          boolean l, boolean ch, boolean wr, int id) { }
        @Override public int[] indices() { return new int[0]; }
        @Override public byte[] sector(int i, int a) { return null; }
        @Override public int[] archives(int i) { return new int[0]; }
        @Override public int archiveCount(int i) { return 0; }
        @Override public int lastArchiveId(int i) { return -1; }
        @Override public int archiveId(int i, int h) { return -1; }
        @Override public int archiveId(int i, String n) { return -1; }
        @Override public int[] files(int i, int a) {
            return i == CONFIGS && a == SEQUENCE ? new int[]{SEQUENCE_ID} : new int[0];
        }
        @Override public int fileCount(int i, int a) { return files(i, a).length; }
        @Override public int lastFileId(int i, int a) {
            int[] files = files(i, a);
            return files.length == 0 ? -1 : files[files.length - 1];
        }
        @Override public byte[][] fileData(int i, int a) { return null; }
        @Override public byte[][] fileData(int i, int a, int[] x) { return null; }
        @Override public byte[] data(int i, int a, int f, int[] x) {
            return values.get(key(i, a, f));
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
