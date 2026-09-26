package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstanceChunkTemplateInteropTest {

    @Test
    void remainsJvmRecordWithStaticConstantsAndFactory() throws Exception {
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(2, 3, 4, 1, 320, 401, 3);

        assertTrue(InstanceChunkTemplate.class.isRecord());
        assertEquals(2, template.targetPlane());
        assertEquals(3, template.sceneChunkX());
        assertEquals(4, template.sceneChunkY());
        assertEquals(1, template.sourcePlane());
        assertEquals(320, template.sourceChunkX());
        assertEquals(401, template.sourceChunkY());
        assertEquals(3, template.rotation());

        assertEquals(8, InstanceChunkTemplate.CHUNK_SIZE);
        assertEquals(-1, InstanceChunkTemplate.ABSENT);
        assertTrue(Modifier.isStatic(
                InstanceChunkTemplate.class
                        .getMethod("decode", int.class, int.class, int.class, int.class)
                        .getModifiers()));
    }

    @Test
    void encodeDecodeRoundTripsPackedFields() {
        InstanceChunkTemplate source =
                new InstanceChunkTemplate(3, 6, 7, 2, 511, 1023, 1);

        Optional<InstanceChunkTemplate> decoded =
                InstanceChunkTemplate.decode(source.encode(), 3, 6, 7);

        assertEquals(source, decoded.orElseThrow());
    }

    @Test
    void absentSentinelShortCircuitsBeforeTargetValidation() {
        assertEquals(
                Optional.empty(),
                InstanceChunkTemplate.decode(InstanceChunkTemplate.ABSENT, -1, -1, -1));
    }

    @Test
    void preservesValidationBoundaries() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(-1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(4, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 4, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 0, 0x400, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 0, 0, 0x800, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 4));
    }

    @Test
    void preservesOriginMathAndRecordStyleOutput() {
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(1, 2, 3, 0, 10, 11, 2);

        assertEquals(80, template.sourceOriginX());
        assertEquals(88, template.sourceOriginY());
        assertEquals(3216, template.sceneOriginX(3200));
        assertEquals(3224, template.sceneOriginY(3200));

        assertEquals(
                "InstanceChunkTemplate[targetPlane=1, sceneChunkX=2, sceneChunkY=3, " +
                        "sourcePlane=0, sourceChunkX=10, sourceChunkY=11, rotation=2]",
                template.toString());
    }
}
