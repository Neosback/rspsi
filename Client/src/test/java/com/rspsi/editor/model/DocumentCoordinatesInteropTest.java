package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentCoordinatesInteropTest {

    @Test
    void preservesJavaAccessorsAndReferencedOwners() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);
        DocumentCoordinates coordinates = new DocumentCoordinates(document, window);

        assertSame(document, coordinates.document());
        assertSame(window, coordinates.window());
    }

    @Test
    void rejectsNullOwnersAndMismatchedDimensions() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldWindow window = new WorldWindow(3200, 4800, 64, 64);

        assertThrows(NullPointerException.class, () -> new DocumentCoordinates(null, window));
        assertThrows(NullPointerException.class, () -> new DocumentCoordinates(document, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentCoordinates(document, new WorldWindow(3200, 4800, 63, 64)));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentCoordinates(document, new WorldWindow(3200, 4800, 64, 63)));
    }

    @Test
    void convertsOnlyTilesAcceptedByBothWindowAndDocument() {
        WorldDocument document = new WorldDocument(64, 64, 2);
        DocumentCoordinates coordinates =
                new DocumentCoordinates(document, new WorldWindow(3200, 4800, 64, 64));

        WorldTile world = new WorldTile(1, 3205, 4807);

        assertEquals(new LocalTile(1, 5, 7), coordinates.toLocal(world).orElseThrow());
        assertEquals(world, coordinates.toWorld(new LocalTile(1, 5, 7)));
    }

    @Test
    void preservesOptionalMissesForNullOutsideAndUnavailablePlanes() {
        WorldDocument document = new WorldDocument(64, 64, 2);
        DocumentCoordinates coordinates =
                new DocumentCoordinates(document, new WorldWindow(3200, 4800, 64, 64));

        assertTrue(coordinates.toLocal(null).isEmpty());
        assertTrue(coordinates.toLocal(new WorldTile(0, 3199, 4800)).isEmpty());
        assertTrue(coordinates.toLocal(new WorldTile(0, 3264, 4800)).isEmpty());
        assertTrue(coordinates.toLocal(new WorldTile(2, 3200, 4800)).isEmpty());
    }

    @Test
    void requireLocalPreservesExceptionBehaviorForMisses() {
        WorldDocument document = new WorldDocument(64, 64, 2);
        DocumentCoordinates coordinates =
                new DocumentCoordinates(document, new WorldWindow(3200, 4800, 64, 64));

        assertThrows(IndexOutOfBoundsException.class,
                () -> coordinates.requireLocal(new WorldTile(0, 3264, 4800)));
        assertThrows(IndexOutOfBoundsException.class, () -> coordinates.requireLocal(null));
    }

    @Test
    void toWorldPreservesNullAndDocumentBoundsFailures() {
        WorldDocument document = new WorldDocument(64, 64, 2);
        DocumentCoordinates coordinates =
                new DocumentCoordinates(document, new WorldWindow(3200, 4800, 64, 64));

        assertThrows(NullPointerException.class, () -> coordinates.toWorld(null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> coordinates.toWorld(new LocalTile(0, 64, 0)));
        assertThrows(IndexOutOfBoundsException.class,
                () -> coordinates.toWorld(new LocalTile(2, 0, 0)));
    }
}
