package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldDocumentBridgeTest {
    @Test
    void ownsEffectivePlaneResolutionForAbridgedColumn() {
        WorldDocument document = new WorldDocument(4, 4, 4);
        document.tile(1, 2, 2).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0,
                OsrsTileFlags.BRIDGE, java.util.List.of()));

        assertEquals(-1, document.effectivePlane(0, 2, 2));
        assertEquals(0, document.effectivePlane(1, 2, 2));
        assertEquals(2, document.effectivePlane(3, 2, 2));
        assertEquals(3, document.effectivePlane(3, 1, 1));
    }

    @Test
    void rejectsCoordinatesOutsideTheDocument() {
        WorldDocument document = new WorldDocument(2, 2, 2);

        assertThrows(IndexOutOfBoundsException.class, () -> document.effectivePlane(0, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> document.effectivePlane(0, 2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> document.effectivePlane(0, 0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> document.effectivePlane(2, 0, 0));
    }

    @Test
    void ignoresBridgeLinksOutsideTheDocument() {
        WorldDocument document = new WorldDocument(2, 2, 2);

        assertEquals(java.util.Optional.empty(), document.bridgeLink(new TileCoordinate(1, 2, 0)));
        assertEquals(java.util.Optional.empty(), document.bridgeLink(new TileCoordinate(1, 0, 2)));
    }
}
