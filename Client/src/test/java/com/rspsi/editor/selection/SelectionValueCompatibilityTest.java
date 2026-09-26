package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SelectionValueCompatibilityTest {

    @Test
    void migratedSelectionsRemainJvmRecordsAndPermittedSelectionTypes() {
        assertTrue(Selection.class.isSealed());

        Set<Class<?>> permitted = Arrays.stream(Selection.class.getPermittedSubclasses())
                .collect(Collectors.toSet());

        for (Class<?> type : List.of(
                FragmentSelection.class,
                ObjectSelection.class,
                TileSelection.class,
                TileAreaSelection.class,
                VertexSelection.class)) {
            assertTrue(type.isRecord(), type.getSimpleName() + " must remain a JVM record");
            assertTrue(Selection.class.isAssignableFrom(type));
            assertTrue(permitted.contains(type),
                    type.getSimpleName() + " must remain in Selection permits");
        }
    }

    @Test
    void fragmentSelectionPreservesNullFailureAndRecordFormatting() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(1, 2, 1, 2),
                List.of(),
                List.of());

        FragmentSelection selection = new FragmentSelection(fragment);
        assertSame(fragment, selection.fragment());
        assertEquals(
                "FragmentSelection[fragment=" + fragment + "]",
                selection.toString());

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new FragmentSelection(null));
        assertEquals("fragment", failure.getMessage());
    }

    @Test
    void objectSelectionPreservesNullFailureAndRecordFormatting() {
        WorldObject object = new WorldObject(100, 10, 2, 0, 3, 4);

        ObjectSelection selection = new ObjectSelection(object);
        assertEquals(object, selection.object());
        assertEquals(
                "ObjectSelection[object=" + object + "]",
                selection.toString());

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new ObjectSelection(null));
        assertEquals("object", failure.getMessage());
    }

    @Test
    void tileSelectionPreservesNullFailureAndRecordFormatting() {
        TileCoordinate coordinate = new TileCoordinate(1, 3, 4);

        TileSelection selection = new TileSelection(coordinate);
        assertEquals(coordinate, selection.coordinate());
        assertEquals(
                "TileSelection[coordinate=" + coordinate + "]",
                selection.toString());

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new TileSelection(null));
        assertEquals("coordinate", failure.getMessage());
    }

    @Test
    void tileAreaSelectionPreservesValidationOrderAndMessages() {
        TileBounds bounds = new TileBounds(3, 4, 5, 6);
        TileAreaSelection selection = new TileAreaSelection(2, bounds);

        assertEquals(2, selection.plane());
        assertEquals(bounds, selection.bounds());
        assertEquals(
                "TileAreaSelection[plane=2, bounds=" + bounds + "]",
                selection.toString());

        IllegalArgumentException planeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new TileAreaSelection(-1, null));
        assertEquals("Selection plane cannot be negative", planeFailure.getMessage());

        NullPointerException boundsFailure = assertThrows(
                NullPointerException.class,
                () -> new TileAreaSelection(0, null));
        assertEquals("bounds", boundsFailure.getMessage());
    }

    @Test
    void vertexSelectionPreservesCornerOrderingAndValidation() {
        VertexSelection selection = new VertexSelection(1, 2, 3, 3);

        assertEquals(1, selection.plane());
        assertEquals(2, selection.x());
        assertEquals(3, selection.y());
        assertEquals(3, selection.corner());
        assertEquals(
                "VertexSelection[plane=1, x=2, y=3, corner=3]",
                selection.toString());

        IllegalArgumentException negativeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new VertexSelection(-1, 0, 0, 0));
        assertEquals("Invalid terrain vertex selection", negativeFailure.getMessage());

        IllegalArgumentException cornerFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new VertexSelection(0, 0, 0, 4));
        assertEquals("Invalid terrain vertex selection", cornerFailure.getMessage());
    }
}
