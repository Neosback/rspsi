package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneTextureDefinitionDecoderTest {
    @Test
    void decodesRevision240TextureRecordUsingBigEndianFields() {
        OpenRuneTextureDefinitionDecoder.TextureRecord record =
                OpenRuneTextureDefinitionDecoder.TextureRecord.decode(17,
                        new byte[]{0x01, 0x2C, 0x34, 0x56, 1, 4, 9});

        assertEquals(300, record.fileId());
        assertEquals(0x3456, record.averageRgb());
        assertTrue(record.transparent());
        assertEquals(4, record.animationDirection());
        assertEquals(9, record.animationSpeed());
    }

    @Test
    void rejectsTruncatedOrWrongProfileTextureRecords() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenRuneTextureDefinitionDecoder.TextureRecord.decode(17,
                        new byte[]{0, 1, 0, 2, 0, 0}));
    }
}
