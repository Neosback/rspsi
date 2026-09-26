package com.rspsi.editor;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectionModelCompatibilityTest {

    @Test
    void modelKeepsPublicFinalNoArgJavaSurface() throws Exception {
        int modifiers = SelectionModel.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertTrue(Modifier.isPublic(
                SelectionModel.class.getDeclaredConstructor().getModifiers()));

        assertNotNull(SelectionModel.class.getDeclaredMethod(
                "select", TileCoordinate.class));
        assertNotNull(SelectionModel.class.getDeclaredMethod(
                "deselect", TileCoordinate.class));
        assertNotNull(SelectionModel.class.getDeclaredMethod("clear"));
        assertNotNull(SelectionModel.class.getDeclaredMethod(
                "contains", TileCoordinate.class));
        assertNotNull(SelectionModel.class.getDeclaredMethod("tiles"));
        assertNotNull(SelectionModel.class.getDeclaredMethod("selectedCoordinates"));
        assertNotNull(SelectionModel.class.getDeclaredMethod("current"));
        assertNotNull(SelectionModel.class.getDeclaredMethod(
                "selectTiles", Set.class));
        assertNotNull(SelectionModel.class.getDeclaredMethod(
                "selectObjects", Set.class));
    }

    @Test
    void tileViewRemainsLiveAndUnmodifiable() {
        SelectionModel model = new SelectionModel();
        Set<TileCoordinate> live = model.tiles();
        TileCoordinate first = new TileCoordinate(0, 1, 2);
        TileCoordinate second = new TileCoordinate(0, 3, 4);

        assertTrue(live.isEmpty());

        model.select(first);
        assertEquals(Set.of(first), live);

        model.select(second);
        assertEquals(Set.of(first, second), live);
        assertInstanceOf(TileSetSelection.class, model.current());

        assertThrows(
                UnsupportedOperationException.class,
                () -> live.add(new TileCoordinate(0, 5, 6)));

        model.clear();
        assertTrue(live.isEmpty());
    }

    @Test
    void selectAndDeselectPreserveUnifiedTileSelectionTransitions() {
        SelectionModel model = new SelectionModel();
        TileCoordinate first = new TileCoordinate(0, 1, 2);
        TileCoordinate second = new TileCoordinate(0, 3, 4);

        model.select(first);
        assertEquals(new TileSelection(first), model.current());

        model.select(second);
        assertInstanceOf(TileSetSelection.class, model.current());

        model.deselect(first);
        assertEquals(new TileSelection(second), model.current());

        model.deselect(second);
        assertNull(model.current());
        assertTrue(model.tiles().isEmpty());
    }

    @Test
    void mutationsNotifyExactlyOnceIncludingNoOpLikeCalls() {
        SelectionModel model = new SelectionModel();
        List<Object> events = new ArrayList<>();
        SelectionChangeListener listener = events::add;
        model.addChangeListener(listener);

        TileCoordinate tile = new TileCoordinate(0, 1, 2);
        model.select(tile);
        model.deselect(new TileCoordinate(0, 9, 9));
        model.deselect(null);
        model.clear();
        model.selectTiles(null);
        model.selectObjects(null);

        assertEquals(6, events.size());
        assertInstanceOf(TileSelection.class, events.get(0));
        assertInstanceOf(TileSelection.class, events.get(1));
        assertInstanceOf(TileSelection.class, events.get(2));
        assertNull(events.get(3));
        assertNull(events.get(4));
        assertNull(events.get(5));

        model.removeChangeListener(listener);
        model.clear();
        assertEquals(6, events.size());
    }

    @Test
    void listenerNullHandlingMatchesJavaModel() {
        SelectionModel model = new SelectionModel();

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> model.addChangeListener(null));
        assertEquals("listener", failure.getMessage());

        assertDoesNotThrow(() -> model.removeChangeListener(null));
    }

    @Test
    void replacementSelectionClearsPriorTilesBeforeValidationFailure() {
        SelectionModel model = new SelectionModel();
        model.select(new TileCoordinate(0, 1, 2));

        NullPointerException objectFailure = assertThrows(
                NullPointerException.class,
                () -> model.selectObject(null));
        assertEquals("object", objectFailure.getMessage());
        assertNull(model.current());
        assertTrue(model.tiles().isEmpty());

        model.select(new TileCoordinate(0, 3, 4));
        IllegalArgumentException areaFailure = assertThrows(
                IllegalArgumentException.class,
                () -> model.selectArea(-1, null));
        assertEquals("Selection plane cannot be negative", areaFailure.getMessage());
        assertNull(model.current());
        assertTrue(model.tiles().isEmpty());

        model.select(new TileCoordinate(0, 5, 6));
        NullPointerException vertexFailure = assertThrows(
                NullPointerException.class,
                () -> model.selectVertex(null));
        assertEquals("vertex", vertexFailure.getMessage());
        assertNull(model.current());
        assertTrue(model.tiles().isEmpty());
    }

    @Test
    void selectTilesPreservesUncheckedNullElementEdgeBehavior() {
        SelectionModel model = new SelectionModel();
        LinkedHashSet<TileCoordinate> source = new LinkedHashSet<>();
        source.add(null);

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> model.selectTiles(source));
        assertEquals("coordinate", failure.getMessage());

        assertTrue(model.contains(null));
        assertTrue(model.tiles().contains(null));
        assertNull(model.current());
    }

    @Test
    void areaSelectedCoordinatesPreserveXThenYIterationOrder() {
        SelectionModel model = new SelectionModel();
        model.selectArea(2, new TileBounds(10, 20, 11, 21));

        assertInstanceOf(TileAreaSelection.class, model.current());
        assertEquals(
                List.of(
                        new TileCoordinate(2, 10, 20),
                        new TileCoordinate(2, 10, 21),
                        new TileCoordinate(2, 11, 20),
                        new TileCoordinate(2, 11, 21)),
                new ArrayList<>(model.selectedCoordinates()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> model.selectedCoordinates().clear());
    }

    @Test
    void singleAndMultiObjectSelectionBehaviorIsPreserved() {
        SelectionModel model = new SelectionModel();
        WorldObject first = new WorldObject(10, 10, 0, 0, 1, 2);
        WorldObject second = new WorldObject(11, 22, 1, 0, 3, 4);

        model.selectObjects(Set.of(first));
        assertEquals(new ObjectSelection(first), model.current());

        LinkedHashSet<WorldObject> multiple = new LinkedHashSet<>();
        multiple.add(first);
        multiple.add(second);
        model.selectObjects(multiple);
        assertEquals(multiple,
                ((com.rspsi.editor.selection.ObjectSetSelection) model.current()).objects());
    }
}
