package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainHeightSourceInteropTest {

    @Test
    void terrainHeightSourceRemainsJvmRecordWithJavaAccessors() {
        TerrainHeightSource source = new TerrainHeightSource(false, 42, true, false);

        assertTrue(TerrainHeightSource.class.isRecord());
        assertFalse(source.generated());
        assertEquals(42, source.explicitValue());
        assertTrue(source.cacheEncoded());
        assertFalse(source.authored());
        assertTrue(source.known());
        assertEquals(new TerrainHeightSource(false, 42, true, false), source);
        assertTrue(source.toString().startsWith("TerrainHeightSource["));
    }

    @Test
    void compatibilityConstructorDefaultsAuthoredFalse() {
        TerrainHeightSource source = new TerrainHeightSource(false, 12, true);

        assertFalse(source.generated());
        assertEquals(12, source.explicitValue());
        assertTrue(source.cacheEncoded());
        assertFalse(source.authored());
    }

    @Test
    void factoryMethodsPreserveDistinctProvenanceStates() {
        TerrainHeightSource generated = TerrainHeightSource.generatedSource();
        TerrainHeightSource explicit = TerrainHeightSource.explicitSource(12);
        TerrainHeightSource authored = TerrainHeightSource.authoredSource();
        TerrainHeightSource unknown = TerrainHeightSource.unknown();

        assertTrue(generated.generated());
        assertTrue(generated.cacheEncoded());
        assertTrue(generated.known());

        assertFalse(explicit.generated());
        assertEquals(12, explicit.explicitValue());
        assertTrue(explicit.cacheEncoded());
        assertTrue(explicit.known());

        assertTrue(authored.authored());
        assertFalse(authored.cacheEncoded());
        assertTrue(authored.known());

        assertFalse(unknown.generated());
        assertFalse(unknown.cacheEncoded());
        assertFalse(unknown.authored());
        assertFalse(unknown.known());
    }

    @Test
    void explicitSourceKeepsCacheOpcodeOneNormalization() {
        TerrainHeightSource source = TerrainHeightSource.explicitSource(1);

        assertEquals(0, source.explicitValue());
        assertTrue(source.cacheEncoded());
        assertFalse(source.generated());
        assertFalse(source.authored());
    }

    @Test
    void validationRejectsInvalidValuesAndGeneratedAuthoredCombination() {
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainHeightSource(false, -1, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainHeightSource(false, 256, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainHeightSource(true, 0, true, true));
    }
}
