package com.rspsi.editor.plugin.event;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectionChangedEventCompatibilityTest {

    @Test
    void eventRemainsJvmRecordWithExactComponentType() {
        assertTrue(SelectionChangedEvent.class.isRecord());

        var components = SelectionChangedEvent.class.getRecordComponents();
        assertEquals(1, components.length);
        assertEquals("selectedTiles", components[0].getName());
        assertEquals(Set.class, components[0].getType());

        var genericType = components[0].getGenericType();
        assertInstanceOf(ParameterizedType.class, genericType);
        ParameterizedType parameterized = (ParameterizedType) genericType;
        assertEquals(Set.class, parameterized.getRawType());
        assertArrayEquals(
                new Object[]{TileCoordinate.class},
                parameterized.getActualTypeArguments());
    }

    @Test
    void nullInputStillNormalizesToImmutableEmptySet() {
        SelectionChangedEvent event = new SelectionChangedEvent(null);

        assertTrue(event.selectedTiles().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.selectedTiles().add(new TileCoordinate(0, 1, 2)));
    }

    @Test
    void constructorPreservesDefensiveImmutableCopy() {
        TileCoordinate first = new TileCoordinate(0, 1, 2);
        TileCoordinate second = new TileCoordinate(0, 3, 4);

        LinkedHashSet<TileCoordinate> source = new LinkedHashSet<>();
        source.add(first);
        source.add(second);

        SelectionChangedEvent event = new SelectionChangedEvent(source);

        source.clear();
        assertEquals(Set.of(first, second), event.selectedTiles());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.selectedTiles().clear());
    }

    @Test
    void nullElementStillFailsDuringSetCopy() {
        LinkedHashSet<TileCoordinate> source = new LinkedHashSet<>();
        source.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new SelectionChangedEvent(source));
    }

    @Test
    void recordEqualityHashCodeAndFormattingRemainJavaRecordStyle() {
        TileCoordinate coordinate = new TileCoordinate(0, 1, 2);
        SelectionChangedEvent first = new SelectionChangedEvent(Set.of(coordinate));
        SelectionChangedEvent second = new SelectionChangedEvent(Set.of(coordinate));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(
                "SelectionChangedEvent[selectedTiles=[" + coordinate + "]]",
                first.toString());
    }
}
