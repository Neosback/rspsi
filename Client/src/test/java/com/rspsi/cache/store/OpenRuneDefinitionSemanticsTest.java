package com.rspsi.cache.store;

import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenRuneDefinitionSemanticsTest {

    @Test
    void defaultObjectAppearanceMatchesOsrsConventions() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(100);
        builder.setName("Default Object");
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        ObjectDefinitionView definition = OpenRuneDefinitionProvider.toView(type);

        // OSRS defaults:
        assertTrue(appearance.castsShadow(), "Default object casts shadow (clipped=true)");
        assertFalse(appearance.occludes(), "Default object does not occlude (modelClipped=false)");
        assertFalse(appearance.mergeNormals(), "Default object does not merge normals (nonFlatShading=false)");
        assertFalse(appearance.nonFlatShading(), "Default object has flat shading");
        assertEquals(0, appearance.contrast(), "Default contrast is 0");
        assertTrue(appearance.randomizeAnimStart(), "Default animation start is randomized");
        assertFalse(appearance.delayAnimationUpdate(), "Default animation update is immediate");

        // Morphs default:
        assertEquals(-1, definition.varbit());
        assertEquals(-1, definition.varp());
        assertEquals(-1, definition.defaultTransform());
        assertEquals(0, definition.transforms().length);
        assertFalse(definition.hasTransforms());
    }

    @Test
    void opcode64DisablesShadowCasting() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(200);
        builder.setClipped(false); // Opcode 64 sets clipped = false
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        assertFalse(appearance.castsShadow(), "Opcode 64 clears shadow casting");
    }

    @Test
    void opcode23EnablesOcclusion() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(300);
        builder.setModelClipped(true); // Opcode 23 sets modelClipped = true
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        assertTrue(appearance.occludes(), "Opcode 23 enables occlusion");
    }

    @Test
    void opcode22EnablesNormalMergingAndNonFlatShading() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(400);
        builder.setNonFlatShading(true); // Opcode 22 sets nonFlatShading = true
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        assertTrue(appearance.mergeNormals(), "Opcode 22 enables normal merging for L-walls and corner locs");
        assertTrue(appearance.nonFlatShading(), "Opcode 22 enables non-flat shading");
    }

    @Test
    void opcode39ScalesRawByteBy25ToClientContrast() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(500);
        builder.setContrast(12); // Raw opcode 39 byte
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        assertEquals(300, appearance.contrast(), "Client contrast must scale raw byte by 25 (12 * 25 = 300)");
    }

    @Test
    void animationFlagsArePreserved() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(600);
        builder.setRandomizeAnimStart(false); // Opcode 89
        builder.setDelayAnimationUpdate(true); // Opcode 90
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);
        assertFalse(appearance.randomizeAnimStart(), "Opcode 89 disables anim start randomization");
        assertTrue(appearance.delayAnimationUpdate(), "Opcode 90 enables delayed animation update");
    }

    @Test
    void morphsAndTransformsArePreservedInDefinitionView() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(700);
        builder.setName("Morphing Gate");
        builder.setMultiVarBit(1042);
        builder.setMultiVarp(88);
        builder.setMultiDefault(999);
        builder.setTransforms(List.of(701, 702, 703, -1, 999));
        ObjectType type = builder.build();

        ObjectDefinitionView definition = OpenRuneDefinitionProvider.toView(type);

        assertEquals(1042, definition.varbit());
        assertEquals(88, definition.varp());
        assertEquals(999, definition.defaultTransform());
        assertTrue(definition.hasTransforms());
        assertArrayEquals(new int[]{701, 702, 703, -1, 999}, definition.transforms());
    }

    @Test
    void opcodes65to72ScaleAndOffsetAxesAreCorrectlyMapped() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder();
        builder.setId(800);
        builder.setModelSizeX(110); // Opcode 65: width X
        builder.setModelSizeZ(140); // Opcode 66: height Y in OSRS
        builder.setModelSizeY(160); // Opcode 67: depth Z in OSRS
        builder.setOffsetX(5);      // Opcode 70: X offset
        builder.setOffsetZ(15);     // Opcode 71: height Y offset in OSRS
        builder.setOffsetY(25);     // Opcode 72: depth Z offset in OSRS
        ObjectType type = builder.build();

        ObjectAppearanceView appearance = OpenRuneDefinitionProvider.toAppearanceView(type);

        assertEquals(110, appearance.scaleX(), "scaleX must map to modelSizeX (opcode 65)");
        assertEquals(140, appearance.scaleY(), "scaleY must map to modelSizeZ (opcode 66: height)");
        assertEquals(160, appearance.scaleZ(), "scaleZ must map to modelSizeY (opcode 67: depth)");
        assertEquals(5, appearance.offsetX(), "offsetX must map to offsetX (opcode 70)");
        assertEquals(15, appearance.offsetY(), "offsetY must map to offsetZ (opcode 71: height)");
        assertEquals(25, appearance.offsetZ(), "offsetZ must map to offsetY (opcode 72: depth)");
    }

    @Test
    void inspect1821Live() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of("/Users/tylercovalt/Desktop/OpenRune Project/OpenRune-Server/.data/cache/LIVE");
        if (!java.nio.file.Files.exists(p)) return;
        OpenRuneCacheStore store = OpenRuneCacheStore.open(p);
        OpenRuneDefinitionProvider provider = (OpenRuneDefinitionProvider) store.definitionProvider(240);
        provider.object(1821).ifPresent(d -> {
            System.out.println("Object 1821 def: name=" + d.displayName() + " models=" + java.util.Arrays.toString(d.modelIds()) + " types=" + java.util.Arrays.toString(d.modelTypes()));
        });
        provider.objectAppearance(1821).ifPresent(a -> {
            System.out.println("Object 1821 app: decorDisplacement=" + a.decorDisplacement() + " offsetX=" + a.offsetX() + " offsetY=" + a.offsetY() + " offsetZ=" + a.offsetZ() + " scaleX=" + a.scaleX() + " scaleY=" + a.scaleY() + " scaleZ=" + a.scaleZ() + " rotated=" + a.rotated());
        });
        provider.modelGeometry(2032).ifPresent(m -> {
            System.out.println("Model 2032: vertices=" + m.vertexCount() + " triangles=" + m.triangleCount());
            int[] pos = m.vertexPositions();
            int minX=Integer.MAX_VALUE, maxX=Integer.MIN_VALUE, minY=Integer.MAX_VALUE, maxY=Integer.MIN_VALUE, minZ=Integer.MAX_VALUE, maxZ=Integer.MIN_VALUE;
            for (int i=0; i<m.vertexCount(); i++) {
                minX = Math.min(minX, pos[i*3]); maxX = Math.max(maxX, pos[i*3]);
                minY = Math.min(minY, pos[i*3+1]); maxY = Math.max(maxY, pos[i*3+1]);
                minZ = Math.min(minZ, pos[i*3+2]); maxZ = Math.max(maxZ, pos[i*3+2]);
            }
            System.out.println("Model 2032 bounds: X=[" + minX + ", " + maxX + "] Y=[" + minY + ", " + maxY + "] Z=[" + minZ + ", " + maxZ + "]");
        });
        com.rspsi.cache.map.MapIndexTable mapTable = com.rspsi.cache.map.MapIndexTable.discover(store, 5);
        for (com.rspsi.cache.map.MapIndexEntry entry : mapTable.entries()) {
            if (entry.objectArchiveId() >= 0) {
                byte[] data = store.read(5, entry.objectArchiveId(), 1);
                if (data == null) data = store.read(5, entry.objectArchiveId(), 0);
                if (data != null) {
                    try {
                        java.util.List<com.rspsi.editor.model.WorldObject> objs =
                                com.rspsi.cache.map.OsrsRegionDecoder.decodeLocations(data);
                        for (com.rspsi.editor.model.WorldObject obj : objs) {
                            if (obj.id() == 1821) {
                                System.out.println("FOUND 1821: Region (" + entry.regionX() + "," + entry.regionY() + ") obj: ID=" + obj.id()
                                        + " plane=" + obj.plane() + " at (" + obj.x() + "," + obj.y() + ") shape=" + obj.type() + " rot=" + obj.rotation());
                                for (com.rspsi.editor.model.WorldObject neighbor : objs) {
                                    if (neighbor.plane() == obj.plane() && neighbor.x() == obj.x() && neighbor.y() == obj.y()) {
                                        System.out.println("   Same tile object: ID=" + neighbor.id() + " shape=" + neighbor.type() + " rot=" + neighbor.rotation());
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
