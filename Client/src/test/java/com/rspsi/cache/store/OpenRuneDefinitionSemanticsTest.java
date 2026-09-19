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
}
