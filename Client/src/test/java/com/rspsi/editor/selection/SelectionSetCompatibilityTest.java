package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectionSetCompatibilityTest {

    @Test
    void setSelectionsRemainJvmRecordsInSealedHierarchy() {
        assertTrue(Selection.class.isSealed());
        assertTrue(TileSetSelection.class.isRecord());
        assertTrue(ObjectSetSelection.class.isRecord());
        assertTrue(Selection.class.isAssignableFrom(TileSetSelection.class));
        assertTrue(Selection.class.isAssignableFrom(ObjectSetSelection.class));

        Set<Class<?>> permitted = Set.of(Selection.class.getPermittedSubclasses());
        assertTrue(permitted.contains(TileSetSelection.class));
        assertTrue(permitted.contains(ObjectSetSelection.class));
    }

    @Test
    void tileSetPreservesDefensiveLinkedHashSetCopy() {
        TileCoordinate first = new TileCoordinate(0, 1, 2);
        TileCoordinate second = new TileCoordinate(0, 3, 4);
        LinkedHashSet<TileCoordinate> source = new LinkedHashSet<>();
        source.add(second);
        source.add(first);
        source.add(second);

        TileSetSelection selection = new TileSetSelection(source);

        source.clear();
        assertEquals(Set.of(first, second), selection.coordinates());
        assertEquals(
                List.of(second, first),
                new ArrayList<>(selection.coordinates()),
                "iteration order must follow the historical LinkedHashSet copy");
        assertThrows(
                UnsupportedOperationException.class,
                () -> selection.coordinates().add(new TileCoordinate(0, 5, 6)));
    }

    @Test
    void tileSetPreservesNullAndEmptyConstructionBehavior() {
        IllegalArgumentException nullFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new TileSetSelection(null));
        assertEquals("A tile selection cannot be empty", nullFailure.getMessage());

        IllegalArgumentException emptyFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new TileSetSelection(Set.of()));
        assertEquals("A tile selection cannot be empty", emptyFailure.getMessage());

        LinkedHashSet<TileCoordinate> containingNull = new LinkedHashSet<>();
        containingNull.add(null);
        TileSetSelection selection = new TileSetSelection(containingNull);
        assertEquals(1, selection.coordinates().size());
        assertTrue(selection.coordinates().contains(null));
    }

    @Test
    void objectSetPreservesDefensiveLinkedHashSetCopy() {
        WorldObject first = new WorldObject(10, 10, 0, 0, 1, 2);
        WorldObject second = new WorldObject(11, 22, 1, 0, 3, 4);
        LinkedHashSet<WorldObject> source = new LinkedHashSet<>();
        source.add(second);
        source.add(first);
        source.add(second);

        ObjectSetSelection selection = new ObjectSetSelection(source);

        source.clear();
        assertEquals(Set.of(first, second), selection.objects());
        assertEquals(
                List.of(second, first),
                new ArrayList<>(selection.objects()),
                "iteration order must follow the historical LinkedHashSet copy");
        assertThrows(
                UnsupportedOperationException.class,
                () -> selection.objects().add(new WorldObject(12, 10, 2, 0, 5, 6)));
    }

    @Test
    void objectSetPreservesNullAndEmptyConstructionBehavior() {
        IllegalArgumentException nullFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectSetSelection(null));
        assertEquals("An object selection cannot be empty", nullFailure.getMessage());

        IllegalArgumentException emptyFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectSetSelection(Set.of()));
        assertEquals("An object selection cannot be empty", emptyFailure.getMessage());

        LinkedHashSet<WorldObject> containingNull = new LinkedHashSet<>();
        containingNull.add(null);
        ObjectSetSelection selection = new ObjectSetSelection(containingNull);
        assertEquals(1, selection.objects().size());
        assertTrue(selection.objects().contains(null));
    }

    @Test
    void recordFormattingAndEqualityRemainJavaRecordStyle() {
        TileCoordinate coordinate = new TileCoordinate(0, 1, 2);
        TileSetSelection tiles = new TileSetSelection(
                new LinkedHashSet<>(List.of(coordinate)));
        assertEquals(
                "TileSetSelection[coordinates=[" + coordinate + "]]",
                tiles.toString());

        WorldObject object = new WorldObject(10, 10, 0, 0, 1, 2);
        ObjectSetSelection objects = new ObjectSetSelection(
                new LinkedHashSet<>(List.of(object)));
        assertEquals(
                "ObjectSetSelection[objects=[" + object + "]]",
                objects.toString());

        assertEquals(
                tiles,
                new TileSetSelection(new LinkedHashSet<>(Arrays.asList(coordinate))));
        assertEquals(
                objects,
                new ObjectSetSelection(new LinkedHashSet<>(Arrays.asList(object))));
    }
}
